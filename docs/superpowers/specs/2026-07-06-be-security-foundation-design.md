# BE 보안 기반(Security + JWT) 골격 설계

> 작성일: 2026-07-06
> 상태: 설계 승인 대기 → 승인 시 구현 계획(writing-plans)으로 전환
> 관련 문서: `docs/DOMAIN-COMMON-STATUTE.md §4`, `docs/DOMAIN-MEMBER-STATUTE.md §3`, `docs/ARCHITECTURE-STATUTE.md §1~2`

---

## 1. 목표와 범위

### 목표
`com.back.global.security`에 **재사용 가능한 인증/인가 인프라 골격**을 구축한다. 이후 MEMBER 도메인이 이 골격 위에 실제 로그인/회원가입을 붙인다.

### 범위 (In)
- Spring Security `SecurityFilterChain` 구성 (STATELESS, JWT 기반)
- JWT 발급·검증 유틸(`JwtProvider`) — access + refresh
- JWT 인증 필터(`JwtAuthenticationFilter`)
- 인증/인가 실패 핸들러 2종 (401/403을 `ApiResponse` JSON으로 응답)
- 권한 표현 `Role` enum (USER / ADMIN)
- 토큰 재발급 엔드포인트 `POST /api/auth/reissue` (무상태)
- `PasswordEncoder`(BCrypt) 빈 — MEMBER용 선행 제공
- 인증 계열 `ErrorCode` 추가 (jjwt 예외별 세분화)

### 범위 밖 (Out)
- 실제 로그인/회원가입 엔드포인트, `Member` 엔티티 → **MEMBER 도메인**에서 구현
- 소셜 로그인(OAuth2 Client) → 별도 작업 (여기 골격이 발급하는 JWT로 수렴)
- refresh 토큰 서버 저장·로테이션·철회 → 추후 Redis 도입 시
- MySQL 데이터소스 설정 → 이 골격은 **DB 불필요**(무상태)

---

## 2. 확정된 결정 (브레인스토밍 결과)

| 항목 | 결정 | 이유 |
|---|---|---|
| 범위 | 순수 인프라 골격 | MEMBER 도메인과 분리, 격리 테스트 가능 |
| 토큰 | access + refresh | MEMBER-STATUTE 기준 |
| refresh 저장 | 무상태(서명 JWT) | 골격 자기완결, DB/Redis 불필요. 로테이션은 추후 |
| 구현 방식 | 커스텀 필터 + `JwtProvider` (접근안 A) | self-issued HS256 토큰에 완전한 제어, 책임 분리·테스트 용이 |
| 라이브러리 | `jjwt` (io.jsonwebtoken) | 스프링부트 JWT 표준 |
| 에러 코드 | jjwt 예외별 세분화 | 만료/변조/형식 구분, 향후 대응 유연 |

---

## 3. 패키지 구조

```
com.back.global.security
├── SecurityConfig.java                   // SecurityFilterChain, PasswordEncoder 빈, 핸들러 등록
├── Role.java                             // enum { USER, ADMIN }
├── jwt
│   ├── JwtProvider.java                  // access/refresh 발급·파싱·검증
│   ├── JwtProperties.java                // @ConfigurationProperties("jwt")
│   └── JwtAuthenticationFilter.java      // OncePerRequestFilter
├── handler
│   ├── JwtAuthenticationEntryPoint.java  // 401 → ApiResponse JSON
│   └── JwtAccessDeniedHandler.java       // 403 → ApiResponse JSON
└── auth
    ├── controller/AuthController.java     // POST /api/auth/reissue
    └── dto/
        ├── ReissueRequest.java            // { refreshToken }
        └── TokenResponse.java             // { accessToken } (필요 시 refreshToken 포함 확장)
```

---

## 4. 컴포넌트 명세

### 4.1 `Role` (enum)
- 값: `USER`, `ADMIN`
- 권한 문자열은 `"ROLE_" + name()` (예: `ROLE_USER`) — Spring Security 관례
- **global.security 소유** — MEMBER의 `Member.role`이 이 enum을 재사용(중복 방지)

### 4.2 `JwtProperties`
- `@ConfigurationProperties(prefix = "jwt")`
- 필드: `secret`(String), `accessTokenExpiry`(long, ms), `refreshTokenExpiry`(long, ms)
- 시크릿은 환경변수 우선(`${JWT_SECRET:...}`), 로컬 기본값만 커밋

### 4.3 `JwtProvider`
- 의존: `JwtProperties`
- HS256, 시크릿 기반 `SecretKey`
- 메서드(초안):
  - `String createAccessToken(Long userId, Role role)` — claims: `sub=userId`, `role="ROLE_x"`, `type="access"`, `iat`, `exp`
  - `String createRefreshToken(Long userId, Role role)` — claims: `sub=userId`, `role="ROLE_x"`, `type="refresh"`, `iat`, `exp` (무상태 재발급 시 role을 알아야 하므로 refresh에도 role 포함)
  - `Claims parse(String token)` — 서명·만료 검증, 실패 시 jjwt 예외 전파
  - `Authentication getAuthentication(String token)` — principal=userId, authorities=`ROLE_x`
- 만료/변조 등은 jjwt 예외(`ExpiredJwtException`, `SignatureException`, `MalformedJwtException`, `UnsupportedJwtException`)로 구분 → 호출측이 ErrorCode로 매핑

### 4.4 `JwtAuthenticationFilter` (`OncePerRequestFilter`)
- `Authorization: Bearer <token>` 추출
- 토큰이 있으면 `JwtProvider.parse` 시도:
  - 성공 → `getAuthentication` 결과를 `SecurityContext`에 저장
  - 실패 → 발생한 jjwt 예외에 대응하는 `ErrorCode`를 **request 속성**에 심고(예: `request.setAttribute("errorCode", EXPIRED_TOKEN)`), 컨텍스트는 비운 채 체인 계속
- 토큰이 없으면 그대로 통과 (인가 단계에서 미인증 처리)

### 4.5 `SecurityConfig`
- `SecurityFilterChain` 빈:
  - `sessionManagement` → `STATELESS`
  - `csrf` disable, `formLogin` disable, `httpBasic` disable
  - `authorizeHttpRequests`:
    - `permitAll`: `/api/auth/**`
    - `hasRole("ADMIN")`: `/api/admin/**`
    - `anyRequest().authenticated()`
  - `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
  - `exceptionHandling`: `authenticationEntryPoint(...)`, `accessDeniedHandler(...)`
- `@Bean PasswordEncoder` → `BCryptPasswordEncoder` (MEMBER 로그인에서 사용)

### 4.6 핸들러 2종
- 필터 체인의 인증/인가 실패는 `@RestControllerAdvice`가 잡지 못하므로(컨트롤러 진입 전) 여기서 직접 응답.
- `JwtAuthenticationEntryPoint`(`AuthenticationEntryPoint`): request 속성의 `ErrorCode`(없으면 `UNAUTHORIZED`)로 `ApiResponse.error` JSON을 `ObjectMapper`로 직렬화, 상태 401.
- `JwtAccessDeniedHandler`(`AccessDeniedHandler`): `FORBIDDEN`으로 JSON 응답, 상태 403.

### 4.7 `AuthController` — `POST /api/auth/reissue`
- 요청: `ReissueRequest { refreshToken }`
- 처리: `JwtProvider.parse(refreshToken)` → `type=="refresh"` 확인 → `sub`+`role`로 새 access 발급 (무상태, DB 조회 없음)
- 응답: `200 ApiResponse<TokenResponse>{ accessToken }`
- 실패: `BusinessException(적절한 ErrorCode)` → `GlobalExceptionHandler`가 처리 (컨트롤러 단이므로 전역 핸들러 적용)
- 무상태: 서버 저장/조회 없음

---

## 5. 데이터 흐름

### 5.1 보호된 요청
```
요청 (Authorization: Bearer <access>)
 → JwtAuthenticationFilter: 추출 → JwtProvider.parse
     · 성공 → Authentication(SecurityContext)
     · 실패 → ErrorCode를 request 속성에 저장, 컨텍스트 비움
 → 인가 규칙(authenticated / hasRole)
     · 통과 → 컨트롤러
     · 미인증 → JwtAuthenticationEntryPoint (401, request 속성 ErrorCode 반영)
     · 권한 부족 → JwtAccessDeniedHandler (403 FORBIDDEN)
```

### 5.2 토큰 재발급
```
POST /api/auth/reissue { refreshToken }
 → AuthController
     · JwtProvider.parse(refresh) + type=="refresh" 검증
     · 유효 → 새 access 발급 → 200 { accessToken }
     · 무효/만료/타입불일치 → BusinessException(ErrorCode) → 401
```

---

## 6. 에러 처리 — ErrorCode 추가

`com.back.global.exception.ErrorCode` enum에 인증 계열을 추가한다. jjwt 예외별로 세분화.

| 상수 | resultCode | HTTP | 메시지 | 발생 지점 |
|---|---|---|---|---|
| `UNAUTHORIZED` | `401-01` | 401 | 인증이 필요합니다. | 토큰 없음/미인증 |
| `MALFORMED_TOKEN` | `401-02` | 401 | 토큰 형식이 올바르지 않습니다. | `MalformedJwtException` |
| `INVALID_SIGNATURE` | `401-03` | 401 | 토큰 서명이 유효하지 않습니다. | `SignatureException` (변조) |
| `EXPIRED_TOKEN` | `401-04` | 401 | 만료된 토큰입니다. | `ExpiredJwtException` |
| `UNSUPPORTED_TOKEN` | `401-05` | 401 | 지원하지 않는 토큰입니다. | `UnsupportedJwtException` |
| `INVALID_TOKEN_TYPE` | `401-06` | 401 | 토큰 종류가 올바르지 않습니다. | reissue에 access 전달 등 |
| `FORBIDDEN` | `403-01` | 403 | 접근 권한이 없습니다. | 권한 부족 |

- 필터: 잡은 jjwt 예외 → 대응 `ErrorCode`를 request 속성에 저장 → 엔트리포인트가 사용
- 컨트롤러(`/reissue`): 검증 실패 시 대응 `ErrorCode`로 `BusinessException` throw

---

## 7. 설정 · 의존성

### `application.yaml`
```yaml
jwt:
  secret: ${JWT_SECRET:local-dev-secret-change-me-in-real-env-0123456789}
  access-token-expiry: 1800000       # 30분 (ms)
  refresh-token-expiry: 1209600000   # 14일 (ms)
```
- 실제 시크릿은 환경변수로 주입, 저장소에 커밋하지 않는다. 로컬 기본값만 둔다.
- HS256 시크릿은 충분한 길이(256bit 이상) 유지.

### `build.gradle.kts` 의존성 추가
```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.x")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.x")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.x")
```
- `spring-boot-starter-security`는 이미 존재.

---

## 8. 테스트 계획 (TDD)

- **JwtProviderTest** (단위)
  - access 발급 → 파싱 왕복 (sub·role claim 일치)
  - refresh 발급 → 파싱 왕복 (type=refresh)
  - 만료 토큰 → `ExpiredJwtException`
  - 서명 변조 토큰 → `SignatureException`
- **SecurityConfig / 필터 통합** (MockMvc + 테스트 전용 더미 컨트롤러)
  - 토큰 없음 → 401 + `ApiResponse` 에러(`401-01`)
  - 유효 access → 200
  - USER 토큰으로 `/api/admin/**` → 403 (`403-01`)
  - ADMIN 토큰으로 `/api/admin/**` → 200
  - 만료 토큰 → 401 (`401-04`), 변조 토큰 → 401 (`401-03`)
- **AuthControllerTest** (`/api/auth/reissue`)
  - 유효 refresh → 새 access (200)
  - 만료 refresh → 401 (`401-04`)
  - access를 refresh 자리에 전달 → 401 (`401-06`)
- 통합 테스트는 웹 슬라이스(`@WebMvcTest` + SecurityConfig import) 또는 standalone MockMvc로 구성해 **JPA/DB 컨텍스트를 띄우지 않는다** (순수 인프라, 무상태)

---

## 9. 문서 영향

- `DOMAIN-COMMON-STATUTE §4`(인증/인가): 필터·핸들러·`Role`·`/reissue`·ErrorCode 반영
- `ErrorCode` 표(6절) 신규 코드 문서화
- `CONTEXT.md`: 보안 골격 완료 상태·주의사항 갱신
- `AI-ACTION-LOGS.md`, TODO 이동(READY→DOING→DONE), 노션 동기화

---

## 10. 향후(Out of scope, 연결 지점)

- **MEMBER 로그인**: `/api/auth/login`(가칭)에서 자격 검증 후 `JwtProvider`로 access+refresh 발급 → 이 골격에 자연스럽게 연결
- **소셜 로그인(OAuth2)**: provider 인증 성공 → `SocialAccount` 조회/생성 → 동일 `JwtProvider` 발급 흐름으로 수렴
- **refresh 로테이션·철회**: Redis 도입 시 화이트리스트/블랙리스트 추가
- **`ErrorCode` 인터페이스화**(보류 중): 도메인별 에러 코드 분리가 필요해지면 재검토. 현재는 전역 enum 단일 유지

---

## 11. 미해결/확인 필요

- `jjwt` 정확한 버전은 구현 시 최신 안정 버전으로 확정 (0.12.x 계열)
- access/refresh 만료 시간(30분/14일)은 초안 — 운영 정책에 따라 조정 가능
