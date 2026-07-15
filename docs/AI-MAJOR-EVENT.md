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

---

## 2026-07-14 — 파일 저장 기반(FileStorage) 구성 및 문서 충돌 해소

### 주요 의사결정
* **파일 메타데이터 구조 확정**: `ARCHITECTURE-STATUTE §3`("메타데이터는 DB에 저장")과 각 도메인이 자체 테이블을 두는 방식 사이에 문서 충돌이 있었음. **공용 `FileMetadata` 엔티티**(`storageKey`/`originalName`/`contentType`/`size`/`uploaderId`) 하나로 통일하기로 확정. 도메인마다 파일 테이블을 중복 정의하지 않고, 도메인 엔티티는 `storageKey`만 보관한다. `DOMAIN-COMMON-STATUTE §7`에 반영.
* **책임 분리**: `FileStorage`(바이트 저장소, DB 모름)와 `FileService`(key 생성 + `FileStorage` 호출 + `FileMetadata` 영속화를 한 트랜잭션으로 묶는 파사드)를 분리. 도메인은 `FileStorage`를 직접 호출하지 않고 항상 `FileService`를 통한다.
* **구현체 전환 지점**: 접근 URL을 `base-url + key`로만 구성해, 로컬↔S3 전환이나 향후 CloudFront/R2 도입이 `base-url` 설정 한 줄 교체로 끝나도록 설계.

### 발견된 버그
* **앱 전역 500→404 라우팅 버그**: 매칭되는 핸들러가 없는 모든 URL(파일 저장 기능과 무관, 앱 전역)에서 Spring이 던지는 `NoResourceFoundException`이 `GlobalExceptionHandler`의 catch-all(`@ExceptionHandler(Exception.class)`)에 걸려 500으로 잘못 응답되고 있었음을 이번 작업 중 발견. 일반 에러코드 `RESOURCE_NOT_FOUND`(404-02) 전용 핸들러를 추가해 404로 정정. 파일 저장 기능이 계기가 됐을 뿐, 수정 자체는 애플리케이션 전역에 적용된다.
* **키 생성 취약점**: 원본 파일명에서 확장자를 추출할 때 점(`.`)이 파일명 맨 앞에 오는 dotfile(예: `.내파일`)을 "확장자 있음"으로 잘못 판단해, "원본 파일명은 key에 넣지 않는다"는 설계 규칙이 깨지고 한글 파일명이 그대로 공개 URL에 노출될 뻔한 문제를 리뷰 중 발견·수정.

### 결정 번복: 파일 저장 기본값을 S3 → 로컬로 전환
* 기존 결정(2026-07-05, 위 참조): "파일 저장은 AWS S3 기반. 로컬 스토리지 방식은 최종적으로 제외." 이 결정을 **오늘(2026-07-14) 번복**한다. `storage.type` 기본값을 `local`로 채택해 로컬 디스크 저장(`LocalFileStorage`)을 기본 구현체로 사용한다.
* 사유: S3 자격증명이 아직 발급되지 않아 실제 버킷 연동을 검증할 수 없는 상태다. 로컬 저장을 기본값으로 두면 자격증명 없이도 개발과 FE 연동을 계속 진행할 수 있고, 업로드한 이미지를 브라우저에서 바로 확인할 수 있다.
* S3(`S3FileStorage`)는 폐기하지 않는다. `storage.type=s3` 설정으로 opt-in 가능하도록 유지하며, 자격증명이 확보되면 운영 환경의 최종 목표(S3, 서울 리전)로 전환한다.

---

## 2026-07-15 — 런타임 DB 구성 및 MEMBER 프로필 설계 확정

### 주요 의사결정
* **런타임 DB**: Docker Compose 기반 MySQL 8.4 채택(재현 가능·버전 고정). 데이터소스는 env 기본값, `ddl-auto`는 개발 단계 `update`(운영 전환 시 validate+마이그레이션 도구 재결정). 테스트는 `application-test.yaml`(H2) 프로파일로 분리 — main yaml에 MySQL이 생기면 `@SpringBootTest`가 MySQL 접속을 시도하므로 `@ActiveProfiles("test")` 필수.
* **악기 표현**: 자유 문자열 대신 **enum 고정 목록(21종)** 채택 — 표기 통일·추후 구인/구직 필터 대비. 노래는 VOICE(성악)/VOCAL(보컬)로 분리(클래식 성악과 대중 보컬은 다른 영역). 밴드/대중 악기(기타/베이스/드럼)는 당분간 미취급으로 제외.
* **장르 필드 제외**: 서비스가 당분간 클래식 중심이라 장르 구분의 실익이 없어 프로필에서 제외. 필요 시 enum 추가로 재도입(하위호환).
* **프로필 생성 시점**: 가입 시 자동 생성하지 않고 첫 수정/업로드 때 생성(lazy upsert). GET은 미생성 시 404가 아닌 빈 기본값 응답(FE 편집 화면 친화).
* **선행 결함 수정**: @Valid 실패·본문 파싱 실패·multipart 파트 누락이 catch-all에 걸려 500으로 응답되던 것을 400-01로 정정.
