# DOMAIN-MEMBER-STATUTE

회원 도메인 구현 규칙.

> 세부 필드는 구현 착수 시 확정하며, 변경 시 이 문서를 갱신한다. 아래는 설계 기준 초안이다.

---

## 1. 패키지

```
com.back.domain.member
├── controller
├── service
├── repository
├── entity
└── dto
```

---

## 2. 엔티티 (구현 반영)

### Member (구현 완료, 2026-07-13)
* id (내부 신원 식별자, 모든 API가 사용 = JWT subject)
* loginId (자체 로그인 아이디, 유니크. **nullable** — 소셜 전용 회원은 없음)
* password (해시 저장. **nullable** — 소셜 전용 회원은 없음)
* email (인증메일 발송·연락용 + 소셜 자동연결 매칭 키. 유니크, **전원 필수**)
* nickname (활동 표시명, 유니크, 필수)
* role (USER / ADMIN. `global.security.Role` 재사용)
* (BaseEntity 상속: createdAt, updatedAt)
* 팩토리: `createLocal(loginId, encodedPassword, email, nickname)` / `createSocial(email, nickname)`

> 자체 로그인 열쇠는 `loginId`, 이메일은 인증·연락 및 소셜 자동연결 매칭 키다. 내부 신원은 `id`.

### MemberProfile
* id
* member (Member 연관)
* instruments (다룰 수 있는 악기 — 다중)
* genres (선호 장르 — 다중)
* bio (자기소개)
* profileImageKey (업로드는 `FileService` 경유. **URL은 저장하지 않는다** — `FileService.getUrl(key)`로 생성, `DOMAIN-COMMON-STATUTE §7`)

### SocialAccount (구현 완료, 2026-07-13)
* id
* member (Member 연관, ManyToOne, non-null)
* provider (`OAuthProvider` enum. 현재 KAKAO. 이후 GOOGLE 등 확장)
* providerUserId (소셜 측 고유 식별자)
* (provider + providerUserId 조합 유니크)

> 악기/장르를 문자열 목록으로 둘지 별도 코드 테이블로 둘지는 구현 시 결정한다.(MemberProfile은 미구현)

---

## 3. 인증 규칙

* 자체 가입: `loginId + password + email + nickname`. 비밀번호는 BCrypt 해시로 저장한다. (2026-07-13, 로그인 열쇠를 email→loginId로 전환)
* 로그인 성공 시 JWT access+refresh 쌍을 발급한다. (자체/소셜 동일)

### 3.1 확정된 API (구현 완료: 자체 가입/로그인 + 카카오 소셜, 2026-07-13)

* `POST /api/auth/signup` : 자체 회원가입. body `{loginId, password, email, nickname}` → `SignupResponse{id, loginId, email, nickname, role}`. 인증 없이 접근 가능해야 하므로 `/api/auth/**`(permit) 아래 둔다.
* `POST /api/auth/login` : 자체 로그인. body `{loginId, password}` → `TokenPairResponse{accessToken, refreshToken}`.
* `POST /api/auth/oauth/kakao` : 카카오 소셜 로그인. body `{code, redirectUri}` → `TokenPairResponse`. 프론트가 카카오에서 받은 1회용 인가코드를 전달하면 백엔드가 교환한다.
* 위치: `com.back.domain.member`(controller/service/repository/entity/dto/oauth). `Member.role`은 `global.security.Role` 재사용.
* 에러코드는 전역 `ErrorCode`에 추가한다(도메인 전용 enum 분리는 보류): `EMAIL_ALREADY_EXISTS`(409-01), `NICKNAME_ALREADY_EXISTS`(409-02), `LOGIN_ID_ALREADY_EXISTS`(409-03), `LOGIN_FAILED`(401-07), `OAUTH_EMAIL_UNVERIFIED`(401-08), `OAUTH_PROVIDER_ERROR`(502-01).
* 자체 로그인 실패는 아이디 부재/비밀번호 없음(소셜 전용)/불일치를 구분하지 않고 `LOGIN_FAILED`(401-07)로 응답한다(계정 존재 여부 노출 방지).

#### 소셜 로그인 흐름 (프론트 주도 + 백엔드 코드교환)
* provider 호출은 `OAuthClient` 인터페이스로 추상화(`KakaoOAuthClient`가 RestClient로 code→token→userinfo). 실패는 `OAUTH_PROVIDER_ERROR`.
* 유저정보 확보 후:
  1. `SocialAccount(provider, providerUserId)` 존재 → 그 회원 로그인
  2. 없음 + **이메일 검증됨(is_email_verified)** + 같은 email 회원 존재 → `SocialAccount` 붙여 자동연결 후 로그인
  3. 없음 + 신규 → `Member.createSocial(email, nickname)` 생성(nickname 충돌 시 유니크 생성) + `SocialAccount` 연결
  * **이메일 미검증/미제공 → `OAUTH_EMAIL_UNVERIFIED` 거절** (미검증 이메일 자동연결은 계정 탈취 벡터이므로 금지)
* 카카오 `client-id`/`client-secret`은 env(`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`) 주입, 커밋 금지.
* 두 방식(자체/소셜) 모두 동일한 `JwtProvider` access+refresh 발급으로 수렴한다.

---

## 4. 권한 규칙

* 신규 회원 기본 권한은 `ROLE_USER`.
* `ROLE_ADMIN` 부여/회수는 관리 기능으로만 수행한다. (일반 API로 노출하지 않음)

---

## 5. 주요 기능(초안)

* 회원가입 (자체)
* 로그인 (자체 / 소셜)
* 내 프로필 조회/수정
* 프로필 이미지 업로드 (FileStorage 경유)
* 다른 회원 프로필 조회 (공개 범위 내)

---

## 6. 테스트

* 회원가입/로그인 성공·실패 케이스
* 중복 loginId/email/nickname 검증
* 소셜 로그인 신규/기존 분기
* 권한별 접근 제어
