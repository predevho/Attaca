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

## 2. 엔티티 초안

### Member
* id
* email (자체 가입 시 로그인 식별자, 유니크)
* password (자체 가입 시. 해시 저장. 소셜 전용 회원은 없을 수 있음)
* nickname (표시 이름, 유니크)
* role (USER / ADMIN)
* (BaseEntity 상속: createdAt, updatedAt)

### MemberProfile
* id
* member (Member 연관)
* instruments (다룰 수 있는 악기 — 다중)
* genres (선호 장르 — 다중)
* bio (자기소개)
* profileImageKey / profileImageUrl (S3 저장, FileStorage 경유)

### SocialAccount
* id
* member (Member 연관)
* provider (KAKAO / GOOGLE 등)
* providerUserId (소셜 측 고유 식별자)
* (provider + providerUserId 조합 유니크)

> 악기/장르를 문자열 목록으로 둘지 별도 코드 테이블로 둘지는 구현 시 결정한다.

---

## 3. 인증 규칙

* 자체 가입: email + password. 비밀번호는 해시(BCrypt 등)로 저장한다.
* 로그인 성공 시 JWT(access, 필요 시 refresh)를 발급한다.
* 소셜 로그인: OAuth2 인증 성공 → providerUserId로 SocialAccount 조회.
  * 존재하면 해당 회원으로 로그인.
  * 없으면 회원(Member) 신규 생성 후 SocialAccount 연결.
* 두 방식 모두 최종적으로 동일한 JWT 발급 흐름으로 수렴한다.

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
* 중복 email/nickname 검증
* 소셜 로그인 신규/기존 분기
* 권한별 접근 제어
