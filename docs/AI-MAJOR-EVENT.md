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
