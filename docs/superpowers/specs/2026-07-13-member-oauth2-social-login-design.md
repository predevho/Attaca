# MEMBER 소셜 로그인(카카오 OAuth2) + 자체 로그인 loginId 전환 설계

> 작성일: 2026-07-13
> 상태: 설계 승인 대기 → 승인 시 구현 계획(writing-plans)으로 전환
> 관련 문서: `docs/DOMAIN-MEMBER-CONSTITUTION.md`, `docs/DOMAIN-MEMBER-STATUTE.md §2~3`, `docs/DOMAIN-COMMON-STATUTE.md §4`
> 선행 완료: BE 보안 골격(2026-07-11), MEMBER 자체 회원가입/로그인(2026-07-12, email 기반)

---

## 1. 목표와 범위

### 목표
카카오 소셜 로그인을 추가하고, 그 과정에서 드러난 로그인 식별자 설계를 정리한다.
자체 로그인 식별자를 `email` → **`loginId`(아이디)** 로 전환하고, 카카오 소셜 로그인을
**프론트 주도 + 백엔드 코드교환(Authorization Code)** 방식으로 붙인다. 모든 인증은 결국
동일한 `JwtProvider`의 access+refresh 발급으로 수렴한다.

### 범위 (In)
- `Member` 모델 개정: `loginId` 필드 추가, 필드별 역할 재정의
- 자체 인증 개정: 회원가입/로그인을 `email` 기반 → `loginId` 기반으로 수정 (기존 커밋 코드 수정)
- 카카오 소셜 로그인 신규: `POST /api/auth/oauth/kakao` (프론트가 전달한 인가코드 검증→JWT 발급)
- provider 추상화: `OAuthClient` 인터페이스 + `KakaoOAuthClient` 구현 (테스트는 Fake로 대체)
- `SocialAccount` 엔티티 및 자동가입/자동연결 로직
- 관련 `ErrorCode` 추가

### 범위 밖 (Out)
- **실제 인증메일 발송 기능** — 지금은 `email` 저장까지만. 메일 인프라 도입은 후속 작업.
- 구글 등 타 provider — `OAuthClient` 어댑터만 추가하면 되도록 구조만 열어둠. 이번엔 카카오만.
- 로그인된 상태에서 수동 "소셜 연결/해제" 관리 UI
- refresh 토큰 서버 저장·로테이션·철회 (현재 무상태 유지)
- FE(Next.js) 구현 — 백엔드 엔드포인트만. 프론트는 인가코드를 전달하는 소비자.

---

## 2. 식별자 역할 정의 (핵심 결정)

로그인 편의를 위해 이메일을 로그인 열쇠로 쓰지 않고 별도 아이디를 둔다.

| 필드 | 역할 | 제약 |
|---|---|---|
| `id` | 내부 신원 식별자. 모든 API가 사용(JWT `subject`) | PK |
| `loginId` | **자체 로그인 열쇠**(아이디+비밀번호) | unique, **nullable**(소셜 전용 회원은 없음) |
| `password` | 자체 로그인 비밀번호(BCrypt 해시) | **nullable**(소셜 전용 회원은 없음) |
| `email` | 인증메일 발송·연락용 + **소셜 자동연결 매칭 키** | unique, **non-null**(전원 필수) |
| `nickname` | 웹 내 활동 표시명 | unique, non-null |
| `role` | 권한(USER/ADMIN). `global.security.Role` 재사용 | non-null |

### nullable 안전성
- nullable은 `loginId`·`password` 둘뿐이며, **소셜 전용 회원에만** null이다.
- 자체 로그인 경로에서 방어: `loginId` 조회 실패 또는 `password == null` → 모두 `LOGIN_FAILED`.
  추가로 `BCryptPasswordEncoder.matches(raw, null)`은 기본적으로 `false`를 반환(이중 안전망).
- `email`은 전원 non-null → email nullable로 인한 위험 없음.

---

## 3. Member 모델 개정

```
Member (com.back.domain.member.entity)
  id        Long        PK, IDENTITY
  loginId   String      unique, nullable
  password  String      nullable            // 해시 저장
  email     String      unique, non-null
  nickname  String      unique, non-null
  role      Role        non-null (EnumType.STRING)
  + BaseEntity(createdAt, updatedAt)

  정적 팩토리
  - createLocal(loginId, encodedPassword, email, nickname) : 자체 가입. role=USER
  - createSocial(email, nickname)                          : 소셜 전용. loginId=null, password=null, role=USER
  - linkable: 기존 회원에 SocialAccount 연결은 SocialAccount 쪽에서 수행
```

```
SocialAccount (com.back.domain.member.entity)
  id              Long          PK, IDENTITY
  member          Member        ManyToOne, non-null
  provider        OAuthProvider enum(KAKAO), non-null
  providerUserId  String        non-null      // 카카오 고유 id
  unique(provider, providerUserId)
  + BaseEntity
```

`OAuthProvider` enum: 우선 `KAKAO`만. 이후 `GOOGLE` 등 추가.

---

## 4. 자체 인증 개정 (email → loginId)

기존 2026-07-12 커밋(email 기반)을 loginId 기반으로 수정한다.

- 회원가입 `POST /api/auth/signup`
  - 요청 `SignupRequest { loginId, password, email, nickname }`
  - 중복 검증: `loginId`/`email`/`nickname` 각각 → 위반 시 409 계열
  - 비밀번호 BCrypt 해시 후 `Member.createLocal(...)` 저장
  - 응답 `SignupResponse { id, loginId, email, nickname, role }`
- 로그인 `POST /api/auth/login`
  - 요청 `LoginRequest { loginId, password }`
  - `findByLoginId` → 없거나 `password == null` → `LOGIN_FAILED`
  - `passwordEncoder.matches` 실패 → `LOGIN_FAILED`
  - 성공 → access+refresh 발급, 응답 `TokenPairResponse { accessToken, refreshToken }`
- 영향 파일: `Member`, `MemberRepository`(findByLoginId·existsByLoginId 추가), `MemberService`,
  `SignupRequest`/`SignupResponse`/`LoginRequest`, 관련 테스트.

---

## 5. 카카오 소셜 로그인 (신규)

### 5.1 흐름 (프론트 주도 + 백엔드 코드교환)
```
① 프론트: "카카오 로그인" → 카카오 인증 페이지로 이동(scope에 account_email 포함)
② 사용자 동의 → 카카오가 프론트 redirect_uri로 [1회용 authorization code] 반환
③ 프론트 → 백엔드: POST /api/auth/oauth/kakao { code, redirectUri }
④ 백엔드(KakaoOAuthClient):
    a. code + client_id + client_secret → 카카오 토큰 엔드포인트 → 카카오 access token
    b. 카카오 access token → 카카오 유저정보(/v2/user/me)
       → OAuthUserInfo { providerUserId, email, emailVerified, nickname }
⑤ 백엔드(MemberService): SocialAccount 조회 → 로그인/자동연결/신규가입 (§5.3)
⑥ 백엔드: 우리 access+refresh 발급 → TokenPairResponse (자체 로그인과 동일 응답)
```

### 5.2 provider 추상화 (테스트 가능성)
```
interface OAuthClient {
    OAuthProvider provider();
    OAuthUserInfo fetch(String code, String redirectUri);   // 토큰교환+유저정보를 캡슐화
}
record OAuthUserInfo(String providerUserId, String email, boolean emailVerified, String nickname) {}

- KakaoOAuthClient : 실제 카카오 HTTP 호출(RestClient). 설정은 env 주입.
- 테스트 : FakeOAuthClient 로 대체 → 외부 호출 없이 MemberService 로직 검증.
- MemberService 는 code→OAuthUserInfo 이후 로직만 담당(HTTP 몰라도 됨).
```
카카오 설정(`OAuthProperties`): `clientId`, `clientSecret`, `tokenUri`, `userInfoUri`.
`client-secret`은 env(`KAKAO_CLIENT_SECRET` 등) 주입, 커밋 금지.

### 5.3 로그인/자동가입/자동연결 규칙
```
OAuthUserInfo 확보 후:
① SocialAccount(KAKAO, providerUserId) 존재 → 연결된 회원 로그인
② 미존재:
    - emailVerified == false 또는 email 없음 → OAUTH_EMAIL_UNVERIFIED (거절)
    - email 로 기존 회원 조회:
        · 있음 → 그 회원에 SocialAccount 생성·연결(자동연결) 후 로그인
        · 없음 → Member.createSocial(email, nickname*) 저장 + SocialAccount 연결(신규 자동가입)
* nickname 은 카카오 닉네임 사용, 충돌 시 짧은 랜덤 접미사로 유니크 생성.
```

### 5.4 자동연결 보안 전제
- 이메일 기준 자동연결은 provider가 **검증한 이메일**일 때만 수행한다.
  미검증 이메일 자동연결은 계정 탈취 벡터이므로 `emailVerified == true`가 아니면 거절한다.
- 카카오는 `kakao_account.is_email_verified`를 제공하므로 이를 신뢰 근거로 사용한다.

---

## 6. ErrorCode 추가 (전역 `ErrorCode`)

기존(EMAIL_ALREADY_EXISTS 409-01, NICKNAME_ALREADY_EXISTS 409-02, LOGIN_FAILED 401-07)은 유지.

| 코드 | resultCode | status | 사유 |
|---|---|---|---|
| `LOGIN_ID_ALREADY_EXISTS` | 409-03 | 409 CONFLICT | 자체 가입 시 loginId 중복 |
| `OAUTH_EMAIL_UNVERIFIED` | 401-08 | 401 UNAUTHORIZED | 카카오 이메일 미검증/미제공으로 가입·연결 거절 |
| `OAUTH_PROVIDER_ERROR` | 502-01 | 502 BAD_GATEWAY | 카카오 토큰/유저정보 호출 실패 |

> 도메인 전용 enum 분리(보류 결정)는 이번에도 유지하지 않고 전역 `ErrorCode`에 추가한다
> (COMMON-STATUTE §2 허용). `BusinessException(ErrorCode)` 호환 유지가 이유.

---

## 7. API 요약

| 메서드 | 경로 | 요청 | 응답 | 인증 |
|---|---|---|---|---|
| POST | `/api/auth/signup` | `{loginId, password, email, nickname}` | `SignupResponse` | permit |
| POST | `/api/auth/login` | `{loginId, password}` | `TokenPairResponse` | permit |
| POST | `/api/auth/oauth/kakao` | `{code, redirectUri}` | `TokenPairResponse` | permit |
| POST | `/api/auth/reissue` | `{refreshToken}` | `TokenResponse` | permit (기존) |

모두 `/api/auth/**`(SecurityConfig permitAll) 아래 → SecurityConfig 변경 불필요.

---

## 8. 테스트 전략 (TDD)

- `MemberTest` : `createLocal`/`createSocial` 필드·기본 role, 소셜 회원 loginId/password null
- `MemberRepositoryTest` : `existsByLoginId`/`findByLoginId`/`existsByEmail`/`findByEmail`,
  `SocialAccountRepository.findByProviderAndProviderUserId`
- `MemberServiceTest` (자체 개정) : loginId 기반 가입/로그인, 중복(loginId/email/nickname),
  password null 회원 로그인 거절
- `MemberServiceTest` (소셜, FakeOAuthClient) :
  - 기존 SocialAccount 로그인
  - email로 기존 회원 자동연결
  - 신규 자동가입(+nickname 충돌 시 유니크 생성)
  - emailVerified=false → `OAUTH_EMAIL_UNVERIFIED` 거절
- `MemberAuthControllerTest` (`@SpringBootTest`) : 자체 signup/login + `/oauth/kakao`
  (테스트 컨텍스트에 FakeOAuthClient 주입) 200/401/409 검증
- `ErrorCodeTest` : 신규 코드 resultCode/status
- 완료 기준: `./gradlew clean build` 전체 통과

---

## 9. 마이그레이션/영향 정리

- 2026-07-12 커밋된 자체 auth(email 기반)를 loginId 기반으로 수정 → 관련 DTO/서비스/테스트 개정
- `Member`에 `loginId` 추가, `email` unique 유지, `password`/`loginId` nullable 전환
- 신규: `SocialAccount`, `OAuthProvider`, `OAuthClient`/`KakaoOAuthClient`, `OAuthUserInfo`,
  `OAuthProperties`, `OAuthLoginRequest`, 소셜 관련 서비스 메서드/컨트롤러 매핑
- env: 카카오 `client-id`/`client-secret` 주입 필요(커밋 금지). 없으면 소셜 경로만 비활성/실패,
  자체 auth·기존 기능은 영향 없음
- 문서 반영: `DOMAIN-MEMBER-STATUTE`(식별자 역할·소셜 API), `CONTEXT`, `TODO-*`, `AI-ACTION-LOGS`
