# Attaca 초기 아키텍처 설계 (Design Spec)

작성일: 2026-07-05
상태: 승인 대기 → 사용자 검토 요청

> 이 문서는 브레인스토밍 결과를 한곳에 모은 마스터 설계 스펙이다.
> 운영 규칙은 `docs/ARCHITECTURE-*.md`, `docs/DOMAIN-*.md`에 반영되어 있으며, 이 문서는 그 근거와 전체 그림을 담는다.

---

## 1. 서비스 정의

Attaca는 음악인들을 위한 커뮤니티 SNS다. 서로 모르는 음악인들이 자유롭게 소통하고, 자신의 연주회를 소개하며, 함께 연주할 사람을 구인/구직으로 찾는 공간이다.

- 주요 사용자: 음악인 (연주자, 밴드, 공연 기획자 등)
- FE와 BE는 완전히 분리한다. 웹으로 시작하되 동일 API를 모바일 앱이 재사용할 수 있어야 한다.

---

## 2. 도메인 구성 (6개)

| 도메인 | 책임 |
|---|---|
| MEMBER | 회원가입/로그인(JWT + OAuth2), 프로필, 권한(ROLE_USER/ROLE_ADMIN) |
| VERIFIED-PERFORMER | 인증 연주자 신청 → 어드민 승인/거절, 어드민 직접 지정, 인증자 공개 프로필 게시 |
| FEED | 자유 게시글, 피드 타임라인, 댓글, 좋아요 |
| PERFORMANCE | 연주회/공연 등록·홍보 (일시·장소·프로그램 등 구조화 개체) |
| RECRUITMENT | 구인/구직 공고 (연주자 찾기 ↔ 참여 희망, 양방향) |
| CHAT | 1:1 / 1:N 실시간 채팅 |

- 어드민은 별도 도메인이 아니라 MEMBER의 `ROLE_ADMIN`이다.
- 관리 기능(예: 인증 연주자 승인)은 각 도메인 안에서 `ROLE_ADMIN` 권한으로 수행한다.

### 도메인 분리 근거: FEED vs PERFORMANCE

연주회는 "제목+본문" 자유 글이 아니라 일시·장소·프로그램 등 고유 속성과 조회 방식(예정/지난 공연, 날짜순)을 가진 구조화 개체다. 따라서 별도 도메인으로 유지한다. 피드에 카드로 노출되더라도 이는 표현(뷰)의 문제이며, 데이터 소유·관리 책임은 PERFORMANCE에 있다. FEED는 서비스 계층을 통해 참조만 한다.

---

## 3. 기술 스택

### Backend
- Java 21, Spring Boot 3.4.x (안정 버전; 4.x 미사용)
- Gradle (Kotlin DSL)
- Spring Data JPA + MySQL
- Spring Security: JWT(자체 가입) + OAuth2(소셜 로그인)
- 실시간 채팅: WebSocket(STOMP) + Redis Pub/Sub
- 파일 저장: AWS S3, `FileStorage` 인터페이스로 추상화

### Frontend
- Next.js (React), 위치 `FE/`

### 패키지 구조
`com.back.domain.<domain>.{controller, service, repository, entity, dto}` + `com.back.global.{config, security, exception, common, storage}`

---

## 4. 데이터 흐름 예시

### 인증 연주자
회원 → 인증 신청(PerformerApplication, PENDING) → 어드민 검토 → 승인 시 VerifiedPerformer 생성 + 공개 프로필 노출 / 거절 시 REJECTED.

### 실시간 채팅
클라이언트 ↔ WebSocket(STOMP) ↔ 서버. 서버 간 브로드캐스트는 Redis Pub/Sub 경유. 메시지는 DB 영속화(히스토리 조회).

### 파일 업로드
도메인 서비스 → `FileStorage.store()` → S3 저장 → 논리 key + 접근 URL 반환 → 도메인 엔티티에 보관. 도메인은 S3 SDK를 직접 다루지 않는다.

---

## 5. 공통 규약

- API 응답: `{ success, data, error }` 래퍼로 통일.
- 예외: `BusinessException` + `ErrorCode` + `@RestControllerAdvice` 전역 처리.
- 엔티티: `BaseEntity`(createdAt, updatedAt) 상속.
- 목록: `Pageable` 기반 페이징.
- DTO: 요청/응답 분리, Entity 노출 금지.
- 테스트: 도메인별 단위·통합 테스트, TDD 우선.

---

## 6. 결정 보류 / 추후

- Nginx 리버스 프록시 + HTTPS + WebSocket 프록시는 배포 단계에서 결정.
- 프로필의 악기/장르를 문자열 목록 vs 코드 테이블로 둘지는 MEMBER 구현 시 확정.
- Presigned URL 직접 업로드는 필요 시 확장.

---

## 7. 다음 단계

1. `BE/build.gradle.kts` Spring Boot 3.4.x로 변경.
2. global 기반(BaseEntity, 응답 래퍼, 예외, 보안, FileStorage) 구성.
3. MEMBER 도메인 자체 회원가입/로그인부터 TDD로 구현.
4. 각 도메인 착수 전 해당 DOMAIN 문서 작성 (VERIFIED-PERFORMER, FEED, PERFORMANCE, RECRUITMENT, CHAT).
