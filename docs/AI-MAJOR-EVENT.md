# AI-MAJOR-EVENT

주요 사건 및 의사결정 기록.

---

## 2026-07-05 — 프로젝트 초기 설계 확정

### 서비스 정의
* Attaca = 음악인 커뮤니티 SNS. 음악인 간 자유로운 소통, 연주회 소개, 구인/구직.

### 도메인 (6개)
* MEMBER, VERIFIED-PERFORMER, FEED, PERFORMANCE, RECRUITMENT, CHAT
* 어드민은 별도 도메인이 아니라 MEMBER의 ROLE_ADMIN으로 결정.

### 주요 의사결정
* **Spring Boot 버전**: 4.1.0 → 3.4.x 안정 버전 사용. (안정성 우선)
* **DB**: MySQL 선택. (자료·호스팅 풍부, JPA 궁합)
* **인증**: 자체(JWT) + 소셜(OAuth2) 병행.
* **채팅**: WebSocket(STOMP) + Redis Pub/Sub. (다중 서버 확장 대비)
* **파일 저장**: AWS S3 기반. `FileStorage` 인터페이스로 추상화(테스트/교체 대비). 로컬 스토리지 방식은 최종적으로 제외.
* **FE**: Next.js(React) 웹 우선. 동일 API를 모바일 앱이 재사용 가능하도록 설계.
* **패키지 구조**: 도메인별 패키지 + 계층형.
* **PERFORMANCE 분리 유지**: 연주회는 일시·장소·프로그램 등 구조화 개체이므로 FEED와 분리. 피드에는 서비스 계층 연동으로 카드 노출.
* **RECRUITMENT 명칭**: "악기/인원 모집" → "구인/구직"(양방향)으로 정의.
* **Nginx**: 배포 단계에서 결정. 로컬 개발에는 미도입.

---

## 2026-07-13 — MEMBER 카카오 소셜 로그인 및 로그인 식별자 설계 확정

### 주요 의사결정
* **로그인 식별자 분리**: 자체 로그인 열쇠를 `email`에서 별도 `loginId`(아이디)로 전환. 이메일 입력 로그인의 불편을 피하기 위함. 역할을 3분할 — `loginId`(로그인 열쇠) / `email`(인증·연락 + 소셜 자동연결 매칭 키) / `nickname`(활동 표시명). 내부 신원은 `member.id`.
* **소셜 로그인 방식**: **프론트 주도 + 백엔드 인가코드 교환**(Authorization Code) 채택. 프론트가 카카오에서 받은 1회용 인가코드를 백엔드로 전달하면 백엔드가 client-secret으로 교환. 무상태 JWT 정책 유지, client-secret 서버 보관, SPA 친화. (대안: 백엔드 리다이렉트형 oauth2Login → 세션 전제라 무상태와 충돌하여 제외)
* **자동연결 보안 정책**: 소셜 이메일과 기존 회원 이메일이 같으면 자동연결하되, **provider가 검증한 이메일(is_email_verified==true)일 때만** 수행. 미검증/미제공은 거절(`OAUTH_EMAIL_UNVERIFIED`). 미검증 이메일 자동연결은 계정 탈취 벡터이므로 금지.
* **소셜 회원 스키마**: 소셜 전용 회원은 `loginId`/`password`가 null(자체 로그인 경로에 null 가드). `email`은 전원 필수(non-null)로 유지해 nullable 위험을 password 하나로 축소.
* **provider 추상화**: `OAuthClient` 인터페이스로 provider 호출을 캡슐화. 카카오 먼저 구현, 구글 등은 어댑터만 추가하면 되도록 개방. 실제 카카오 HTTP 연동은 자동 테스트 범위 밖(env 키로 수동 검증), 자동 테스트는 매핑·서비스 로직만(Fake/H2).
* **에러코드 정책 유지**: 도메인 전용 enum 분리는 계속 보류하고 전역 `ErrorCode`에 추가(409-03/401-08/502-01).
