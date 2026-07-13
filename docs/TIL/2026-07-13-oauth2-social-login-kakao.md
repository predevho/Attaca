# TIL — OAuth2 소셜 로그인(카카오)을 무상태 JWT API에 붙이기

> 작성일: 2026-07-13
> 목적: Attaca의 카카오 소셜 로그인 구현(`com.back.domain.member`)에 쓰인 OAuth2 흐름과 설계 판단을, **이 문서 하나로** 이해할 수 있게 정리한다.
> 관련 코드: `domain.member.oauth`, `domain.member.service.MemberOAuthService` / 규칙: `docs/DOMAIN-MEMBER-STATUTE.md §3`

---

## 1. 먼저 큰 그림 — "소셜 로그인"이 실제로 하는 일

소셜 로그인은 **비밀번호 대신 카카오가 신원을 보증**해 주는 것이다. 우리는 카카오에게 "이 사람 맞아?"를 확인받고, 확인되면 **우리 서비스의 JWT**를 발급한다. 즉:

> 자체 로그인: (아이디+비번) → 우리가 검증 → 우리 JWT
> 소셜 로그인: (카카오 인가코드) → 카카오가 검증 → 우리 JWT

**로그인 방식만 다르고, 발급 결과(access+refresh)와 이후 API 사용은 완전히 동일**하다. 둘 다 같은 `JwtProvider`로 수렴한다. 이게 핵심이다.

---

## 2. 왜 "프론트 주도 + 백엔드 코드교환"인가

OAuth2를 붙이는 방식은 여러 가지인데, 우리는 **무상태 JWT + 별도 SPA(Next.js)** 라는 전제 때문에 방식이 갈렸다.

| 방식 | 설명 | 우리 상황 |
|---|---|---|
| 백엔드 리다이렉트(oauth2Login) | 브라우저가 백엔드로 이동, 백엔드가 세션 기반 처리 | **세션 전제 → 무상태와 충돌**. 제외 |
| **프론트 주도 + 백엔드 코드교환** | 프론트가 인가코드 받고 백엔드가 교환 | **채택**. 무상태 유지, secret 서버 보관 |
| 프론트가 provider 토큰 전달 | 프론트가 provider 토큰까지 받아 전달 | 앱키 노출·보안 약함. 제외 |

**Authorization Code(인가코드) 흐름**이 표준인 이유: 실제 토큰 교환에 **client-secret**이 필요한데, 이걸 프론트(브라우저)에 두면 노출된다. 그래서 "코드"라는 **1회용·단기 교환권**만 프론트가 받고, 그걸 **백엔드가 secret으로 진짜 토큰과 교환**한다.

---

## 3. 실제 흐름 (단계별)

```
① 프론트: "카카오 로그인" → 카카오 인증 페이지 (scope에 account_email 포함)
② 사용자 동의 → 카카오가 프론트 redirect_uri 로 [1회용 인가코드] 반환
③ 프론트 → 백엔드: POST /api/auth/oauth/kakao { code, redirectUri }
④ 백엔드(KakaoOAuthClient):
     a. code + client_id + client_secret → 카카오 토큰 엔드포인트 → 카카오 access token
     b. 카카오 access token → 카카오 유저정보(/v2/user/me)
        → OAuthUserInfo { providerUserId, email, emailVerified, nickname }
⑤ 백엔드(MemberOAuthService): SocialAccount 조회 → 로그인/자동연결/신규가입 (§4)
⑥ 백엔드: 우리 access+refresh 발급 → 자체 로그인과 동일한 TokenPairResponse
```

> 1회용 코드를 **발급하는 주체는 카카오**, 프론트는 **받아서 전달만**, **검증(교환)은 백엔드**가 한다.

---

## 4. 로그인 / 자동가입 / 자동연결 — 그리고 보안

카카오 유저정보를 확보한 뒤 세 갈래로 나뉜다.

```
① SocialAccount(KAKAO, providerUserId) 있음 → 그 회원 로그인
② 없음:
   - emailVerified == false 또는 email 없음 → 거절 (OAUTH_EMAIL_UNVERIFIED)
   - email 로 기존 회원 조회:
       · 있음 → 그 회원에 SocialAccount 붙여 자동연결 후 로그인
       · 없음 → 신규 소셜 회원 생성(nickname 충돌 시 유니크) + 연결
```

### ⚠️ 왜 "검증된 이메일"만 자동연결하나 (핵심 보안)
이메일로 기존 회원을 찾아 붙이는 건 편리하지만, **provider가 이메일을 검증하지 않으면 계정 탈취**가 된다:
> 공격자가 피해자 이메일로 (미검증) 소셜 계정을 만들어 로그인 → 우리가 이메일만 보고 피해자 계정에 붙임 → 탈취

그래서 **`is_email_verified == true`일 때만** 자동연결·가입을 허용하고, 미검증/미제공은 거절한다. 카카오는 이 플래그를 제공하므로 신뢰 근거로 쓴다. 이 순서(가드가 조회·연결보다 **먼저**)를 회귀 테스트로 고정해 두었다.

---

## 5. 식별자 3분할 — loginId / email / nickname

소셜을 붙이며 로그인 식별자 설계를 정리했다.

| 필드 | 역할 | 제약 |
|---|---|---|
| `loginId` | 자체 로그인 열쇠(아이디) | unique, **nullable**(소셜 전용은 없음) |
| `email` | 인증·연락 + **소셜 자동연결 매칭 키** | unique, **전원 필수** |
| `nickname` | 웹 내 활동 표시명 | unique |
| `id` | 내부 신원(모든 API가 사용 = JWT subject) | PK |

- 이메일 입력 로그인이 불편해서 **아이디(loginId)로 로그인**하도록 분리.
- nullable은 `loginId`·`password` 둘뿐이고 소셜 전용 회원에만 null. 자체 로그인 경로에서 `password == null`을 가드해 소셜 회원의 비번 로그인을 차단(추가로 `matches(raw, null)`도 false 반환 — 이중 안전망).

---

## 6. provider 추상화 — 확장과 테스트를 동시에

```java
interface OAuthClient {
    OAuthProvider provider();                       // KAKAO
    OAuthUserInfo fetch(String code, String redirectUri);   // 토큰교환+유저정보 캡슐화
}
```

- `KakaoOAuthClient`가 실제 HTTP(RestClient, 5s 타임아웃)를 담당. 실패는 `OAUTH_PROVIDER_ERROR`로 변환.
- 구글 등은 **어댑터(OAuthClient 구현)만 추가**하면 되고, `MemberOAuthService`는 provider별 코드를 몰라도 된다.
- 덕분에 서비스 로직은 **외부 HTTP 없이 Fake로 테스트** 가능. 실제 카카오 호출은 자동 테스트 밖(env 키로 수동 검증), 자동 테스트는 매핑(`toUserInfo`)·서비스 로직(자동가입/연결/거절)만 커버.

---

## 7. 실제 카카오 연동에 필요한 것 (코드 밖 준비물)

1. 카카오 개발자앱 등록 → REST API 키(client-id) / client-secret, **Redirect URI 등록**, 동의항목에 **카카오계정(이메일)** 추가
2. 서버 env 주입: `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` (커밋 금지)
3. `POST /api/auth/oauth/kakao { code, redirectUri }`로 실제 토큰 발급 확인

---

## 한 줄 지도(총정리)
> **프론트가 카카오 인가코드를 받아 백엔드에 넘김 → 백엔드가 secret으로 토큰·유저정보 교환 → (검증된 이메일 기준) 로그인/자동연결/자동가입 → 자체 로그인과 똑같은 우리 JWT 발급.**
> "인증 방식만 다르고 결과는 하나로 수렴한다"만 기억하면 나머지는 제자리를 찾는다.
