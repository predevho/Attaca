# TIL — Spring Security + JWT 무상태 인증 (한 문서로 이해하기)

> 작성일: 2026-07-07
> 목적: Attaca의 보안 골격(`com.back.global.security`)에 쓰인 Spring Security 설정과 개념을, **이 문서 하나만 봐도** 흐름과 각 메서드의 의미를 이해할 수 있게 정리한다.
> 관련 코드: `global.security` 패키지 / 규칙: `docs/DOMAIN-COMMON-STATUTE.md §4`

---

## 목차
1. 먼저 알아야 할 오해 — "기본값" vs "우리 설정"
2. 두 단어: 인증(Authentication) vs 인가(Authorization)
3. Spring Security의 뼈대 — 필터 체인
4. 핵심 등장인물(개념) 5가지
5. 우리 코드의 요청 흐름 (단계별)
6. `SecurityConfig` 한 줄씩 해설
7. 컴포넌트별 역할 (우리가 만든 클래스)
8. 토큰 이야기 — access / refresh / Bearer
9. 인증 에러 코드 매핑
10. 왜 이 조합이 "표준"인가
11. 앞으로(확장 지점)
12. 용어·클래스 위치 사전

---

## 1. 먼저 알아야 할 오해 — "기본값" vs "우리 설정"

Spring Security는 **"secure by default"** 다. `spring-boot-starter-security` 의존성만 추가하고 아무 설정도 안 하면:

- **모든 URL이 인증을 요구**하며 (401),
- 스프링이 만든 **로그인 폼**과 **HTTP Basic**이 켜지고,
- 콘솔에 **임시 비밀번호**가 찍히며,
- **세션(JSESSIONID 쿠키)** 기반으로 로그인 상태를 기억하고,
- **CSRF 보호**가 켜진다.

즉 기본값은 **"세션 + 폼 로그인"** 방식이다. 우리가 만든 건 이 기본을 **끄고**, 대신 **"토큰(JWT) 기반 무상태"** 방식으로 다시 세팅한 것이다.

| 항목 | Spring Security 기본값 | 우리 설정(JWT API 표준) |
|---|---|---|
| 로그인 상태 저장 | 세션(서버 메모리 + 쿠키) | **무상태**(요청마다 토큰) |
| 로그인 방식 | 폼 로그인 / HTTP Basic | JWT 토큰 |
| CSRF | 켜짐 | **끔**(무상태라 불필요) |
| 인증 실패 응답 | 로그인 폼으로 리다이렉트 | **JSON(`ApiResponse`) 401** |

> 그래서 "이게 스프링 시큐리티 기본 세팅이야?"의 정확한 답은:
> **"기본을 끄고 JWT 무상태 REST API의 표준 패턴으로 구성한 것"** 이다. 이 패턴은 토큰 기반 API에서 거의 관용구처럼 쓰인다.

---

## 2. 두 단어: 인증 vs 인가

보안의 절반은 이 둘을 구분하는 것에서 시작한다.

- **인증(Authentication) = "너 누구야?"** — 신원 확인.
  - 예: 토큰을 보고 "이 요청은 회원 7번이 보냈다"를 확인.
- **인가(Authorization) = "너 여기 들어와도 돼?"** — 권한 확인.
  - 예: "이 URL은 ADMIN만" 규칙에 걸리는지 검사.

**순서가 중요하다: 인증(누구) 먼저 → 인가(권한) 나중.**
실패도 각각 다르게 응답한다: 인증 실패 = **401 Unauthorized**, 인가 실패 = **403 Forbidden**.

---

## 3. Spring Security의 뼈대 — 필터 체인

Spring Security는 **서블릿 필터 체인(Servlet Filter Chain)** 위에서 동작한다. 요청이 **컨트롤러에 도착하기 전에** 여러 필터를 순서대로 통과하는데, 그 앞쪽에 보안 필터들이 줄지어 있다.

```
요청 → [보안 필터들 ... → 우리의 JwtAuthenticationFilter → ...] → DispatcherServlet → 컨트롤러
```

**이게 Spring Security가 어렵게 느껴지는 핵심 이유다.** 우리가 평소 짜는 Controller/Service 코드보다 **한 겹 아래**, "요청이 컨트롤러에 닿기 전" 세계에서 인증/인가가 벌어지기 때문이다.

그래서 인증/인가 실패는 **컨트롤러 밖(필터 단계)**에서 발생하고, `@RestControllerAdvice`(GlobalExceptionHandler)로는 못 잡는다 → 별도 **핸들러**가 필요하다(§7).

---

## 4. 핵심 등장인물(개념) 5가지

| 개념 | 한 줄 정의 | 비유(건물 보안) |
|---|---|---|
| **Authentication** | 인증된 사용자 1명을 담는 객체(누구+권한+인증여부) | 목에 건 방문자 태그 |
| **principal** | "누구" — 우리는 회원 id(Long) | 태그에 적힌 이름 |
| **GrantedAuthority** | 권한 1개 (예: `ROLE_USER`) | 출입 등급 |
| **SecurityContext(Holder)** | 인증 결과를 요청 처리 내내 담아두는 보관소 | 경비실 방문자 명부 |
| **필터(OncePerRequestFilter)** | 요청 1건당 한 번 실행되는 관문 로직 | 정문 경비원 |

**연결 고리(가장 중요):**
> 필터가 토큰을 검증해 `Authentication`을 만들고 → `SecurityContextHolder`에 넣으면 → 이후 **인가 규칙**이 그걸 꺼내 "권한 되나?"를 판단한다.
> 이 **"넣고–꺼내기"**가 인증과 인가를 잇는 다리다.

---

## 5. 우리 코드의 요청 흐름 (단계별)

### (A) 보호된 API 요청
```
① 요청 (Authorization: Bearer <access>)
② JwtAuthenticationFilter
     · 헤더에서 토큰 추출
     · JwtProvider.parse 로 서명·만료 검증
        - 성공 → Authentication 생성 → SecurityContext에 저장
        - 실패 → 사유 ErrorCode를 request 속성에 저장(응답은 아직 안 만듦), 통과
③ 인가 규칙(SecurityConfig)
     · 인증됐나? 권한 되나?
④ 분기
     · 인증+권한 OK      → 컨트롤러 실행 → 200
     · 미인증            → JwtAuthenticationEntryPoint → 401
     · 인증됐지만 권한 X → JwtAccessDeniedHandler       → 403
```

### (B) 토큰 재발급
```
POST /api/auth/reissue { refreshToken }
 → AuthController
     · refresh 파싱(서명·만료 검증)
     · type == "refresh" 확인 (access를 넣으면 거절)
     · sub(회원id) + role 로 새 access 발급
 → 200 { accessToken }
```
무상태라 서버에 refresh를 저장/조회하지 않는다. 그래서 재발급 때 role을 알아야 하므로 **refresh 토큰에도 role claim을 담아둔다.**

---

## 6. `SecurityConfig` 한 줄씩 해설

```java
@Configuration
@EnableWebSecurity          // 스프링 시큐리티 웹 보안 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

| 코드 | 의미 |
|---|---|
| `@EnableWebSecurity` | 시큐리티 필터 체인을 켠다 |
| `SecurityFilterChain filterChain(HttpSecurity http)` | **보안 정책을 조립해 반환하는 빈**. 스프링이 이걸 필터 체인으로 사용 |
| `csrf(...).disable()` | CSRF 보호 끔. 무상태(쿠키·세션 미사용)라 CSRF 공격 표면이 없음 |
| `formLogin(...).disable()` | 기본 로그인 폼 끔 (우리는 토큰 인증) |
| `httpBasic(...).disable()` | HTTP Basic 끔 |
| `sessionManagement(... STATELESS)` | **세션을 만들지도 쓰지도 않음.** 매 요청을 토큰만으로 판단 |
| `authorizeHttpRequests(...)` | **인가 규칙 블록** 시작 |
| `.requestMatchers("/api/auth/**").permitAll()` | 로그인·재발급 등은 인증 없이 허용 |
| `.requestMatchers("/api/admin/**").hasRole("ADMIN")` | `ROLE_ADMIN` 권한 필요 (`hasRole`은 앞에 `ROLE_`를 자동으로 붙여 비교) |
| `.anyRequest().authenticated()` | 나머지는 전부 인증 필요 |
| `exceptionHandling(...)` | 인증 실패(401)·인가 실패(403) 담당자 등록 |
| `addFilterBefore(myFilter, UsernamePasswordAuthenticationFilter.class)` | 우리 JWT 필터를 표준 로그인 필터 **앞에** 끼움 → 토큰을 먼저 처리 |
| `http.build()` | 조립 완료 → `SecurityFilterChain` 반환 |
| `PasswordEncoder = BCryptPasswordEncoder` | 비밀번호 단방향 해시 인코더. MEMBER 로그인에서 쓸 예정이라 미리 빈 등록 |

> **왜 `hasRole("ADMIN")`인데 코드엔 `ROLE_ADMIN`?** — Spring Security 관례상 권한 문자열은 `ROLE_` 접두사를 붙인다. `hasRole("ADMIN")`은 내부적으로 `ROLE_ADMIN`을 찾는다. 그래서 우리 `Role.authority()`가 `"ROLE_" + name()`을 반환한다.

---

## 7. 컴포넌트별 역할 (우리가 만든 클래스)

`com.back.global.security` 아래 **우리가 작성한** 클래스들. 각자 한 가지 일만 한다.

| 클래스 | 책임 | 핵심 |
|---|---|---|
| `JwtProvider` | 토큰 **발급·파싱·인증변환** | `createAccessToken`/`createRefreshToken`/`parse`/`getAuthentication` |
| `JwtProperties` | 설정값(시크릿·만료) | `@ConfigurationProperties("jwt")` |
| `JwtAuthenticationFilter` | 요청마다 토큰 **검증→SecurityContext** | `OncePerRequestFilter` 상속 |
| `SecurityConfig` | **정책** 정의 + 필터·핸들러 등록 | `SecurityFilterChain` 빈 |
| `JwtAuthenticationEntryPoint` | **인증 실패(401)** JSON 응답 | `AuthenticationEntryPoint` 구현 |
| `JwtAccessDeniedHandler` | **인가 실패(403)** JSON 응답 | `AccessDeniedHandler` 구현 |
| `Role` | 권한 표현(USER/ADMIN) | `authority()` → `ROLE_x` |
| `AuthController` | `/api/auth/reissue` | 무상태 재발급 |

**왜 핸들러 2종이 따로 필요한가(중요):**
인증/인가 실패는 §3처럼 **필터 단계**(컨트롤러 밖)에서 난다. `@RestControllerAdvice`는 컨트롤러에서 난 예외만 잡으므로 여기선 못 쓴다. 그래서 두 핸들러가 `ObjectMapper`로 `ApiResponse` JSON을 **직접** 써서, 컨트롤러 응답과 **같은 포맷**을 유지한다.

---

## 8. 토큰 이야기 — access / refresh / Bearer

### access vs refresh
| | accessToken | refreshToken |
|---|---|---|
| 역할 | 하루짜리 출입 배지 | 장기 사원증 |
| 언제 | **모든 API 요청**에 첨부 | access 만료 시 **재발급에서만** |
| 수명 | 짧음(30분) | 김(14일) |
| 노출 | 잦음 | 드묾 |

**왜 둘로 나누나?** 보안·편의의 균형. access를 짧게 해 탈취 피해를 줄이고, refresh로 재로그인 빈도를 줄인다. refresh는 `/reissue`에서만 쓰여 덜 노출된다.

### Bearer란?
`Authorization: Bearer <token>` 형식에서 **Bearer = "소지자"**. HTTP 표준(RFC 6750)의 인증 스킴 이름이다.
- 의미: **"이 토큰을 가진 자에게 권한을 준다"** — 토큰 자체가 열쇠. 들고 있으면 통과.
- 그래서 탈취되면 위험 → **HTTPS 필수 + access 짧은 만료**로 방어.
- `Bearer` 외의 스킴: `Basic`(아이디:비번), `Digest` 등.
- 우리 필터의 `resolveToken`이 `"Bearer "`(7글자)를 떼고 순수 토큰만 꺼낸다.

---

## 9. 인증 에러 코드 매핑

`ErrorCode`에 정의. 필터가 jjwt 예외를 분류해 request 속성에 심고, 엔트리포인트가 응답한다.

| ErrorCode | resultCode | HTTP | 원인 |
|---|---|---|---|
| `UNAUTHORIZED` | 401-01 | 401 | 토큰 없음 / 미인증 |
| `MALFORMED_TOKEN` | 401-02 | 401 | 형식 오류(`MalformedJwtException`) |
| `INVALID_SIGNATURE` | 401-03 | 401 | 서명 변조(`SignatureException`) |
| `EXPIRED_TOKEN` | 401-04 | 401 | 만료(`ExpiredJwtException`) |
| `UNSUPPORTED_TOKEN` | 401-05 | 401 | 미지원(`UnsupportedJwtException`) |
| `INVALID_TOKEN_TYPE` | 401-06 | 401 | reissue에 access 전달 등 |
| `FORBIDDEN` | 403-01 | 403 | 인증됐으나 권한 부족 |

---

## 10. 왜 이 조합이 "표준"인가

토큰 기반 REST API라면 거의 항상 다음 조합이 반복된다. 이유까지 알면 외울 필요가 없다.

- **STATELESS 세션** — API는 여러 서버로 확장(수평 스케일)되는데, 세션을 서버 메모리에 두면 서버마다 로그인 상태가 달라진다. 토큰은 요청이 스스로 신분을 증명하므로 어느 서버로 가도 동작한다.
- **csrf 비활성** — CSRF는 브라우저가 쿠키를 자동 첨부하는 걸 악용하는 공격이다. 우리는 쿠키가 아니라 `Authorization` 헤더에 토큰을 **명시적으로** 실으므로 그 공격이 성립하지 않는다.
- **formLogin/httpBasic 비활성** — 서버가 화면(로그인 폼)을 그리지 않는 순수 API이기 때문. 인증은 토큰으로.
- **커스텀 필터 + EntryPoint/AccessDeniedHandler** — 실패를 리다이렉트가 아니라 **일관된 JSON**으로 돌려주기 위해.

> 요약: **"확장 가능하고, 화면이 없고, 응답이 JSON인 API"** 라는 전제에서 자연스럽게 도출되는 설정이다.

---

## 11. 앞으로(확장 지점)

- **MEMBER 로그인**: `/api/auth/login`(가칭)에서 email/비번을 `PasswordEncoder`로 검증 → `JwtProvider`로 access+refresh 발급. 이 골격 위에 그대로 얹힌다.
- **소셜 로그인(OAuth2)**: 카카오/구글 인증 성공 → 회원 조회/생성 → 동일한 `JwtProvider` 발급 흐름으로 수렴.
- **refresh 로테이션·강제 만료**: 지금은 무상태라 서버가 refresh를 못 무효화한다. 보안을 강화하려면 Redis에 화이트리스트/블랙리스트를 두는 방식으로 확장.

---

## 12. 용어·클래스 위치 사전

**우리가 만든 클래스** (`com.back.global.security.*`, 소스 폴더에 있음):
`JwtProvider`, `JwtProperties`, `JwtAuthenticationFilter`, `SecurityConfig`, `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`, `Role`, `AuthController`

**라이브러리 클래스** (우리가 상속/구현/호출만; 정의는 의존성 jar 안. IDE에서 `import` 줄을 Ctrl+클릭하면 열림):

| 이름 | 패키지 | 우리 코드에서 |
|---|---|---|
| `Authentication` | `org.springframework.security.core` | `JwtProvider` 반환 타입 |
| `UsernamePasswordAuthenticationToken` | `org.springframework.security.authentication` | `JwtProvider` |
| `GrantedAuthority`/`SimpleGrantedAuthority` | `org.springframework.security.core.authority` | `JwtProvider` |
| `SecurityContextHolder` | `org.springframework.security.core.context` | `JwtAuthenticationFilter` |
| `OncePerRequestFilter` | `org.springframework.web.filter` | `JwtAuthenticationFilter` 상속 |
| `AuthenticationEntryPoint` | `org.springframework.security.web` | `JwtAuthenticationEntryPoint` 구현 |
| `AccessDeniedHandler` | `org.springframework.security.web.access` | `JwtAccessDeniedHandler` 구현 |
| `HttpSecurity` | `...config.annotation.web.builders` | `SecurityConfig` |
| `SecurityFilterChain` | `org.springframework.security.web` | `SecurityConfig` 반환 |
| `SessionCreationPolicy` | `org.springframework.security.config.http` | `SecurityConfig` |
| `PasswordEncoder`/`BCryptPasswordEncoder` | `...security.crypto.password` / `.bcrypt` | `SecurityConfig` |
| `Jwts`/`Claims` (jjwt) | `io.jsonwebtoken` | `JwtProvider` |
| `Keys` (jjwt) | `io.jsonwebtoken.security` | `JwtProvider` |

---

## 한 줄 지도(총정리)
> **jjwt가 토큰을 만들고 읽음 → 필터가 검증해 SecurityContext에 `Authentication`(principal+authority) 저장 → HttpSecurity 인가 규칙이 그걸 보고 통과/거절 → 거절이면 EntryPoint(401)/AccessDeniedHandler(403)가 JSON 응답.**
> 이 순서만 기억하면 각 메서드가 "이 단계에서 이걸 하는구나"로 제자리를 찾는다.
