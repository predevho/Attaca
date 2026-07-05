# CONTEXT

현재 작업 수행에 필요한 최소 정보만 유지한다. 로그 저장소로 쓰지 않는다.

---

## 현재 상태

* 단계: BE 전역(global) 기반 구성 완료(ApiResponse/BaseEntity/예외체계/JpaAuditing). 다음은 보안 기반(Security+JWT).
* 확정된 기술 스택
  * BE: Spring Boot 3.4.x / Java 21 / MySQL / Spring Security(JWT + OAuth2) / WebSocket(STOMP)+Redis / S3(FileStorage 추상화)
  * FE: Next.js (React), 위치 `FE/` (아직 비어 있음)
* 도메인 6개: MEMBER, VERIFIED-PERFORMER, FEED, PERFORMANCE, RECRUITMENT(구인/구직), CHAT
* 어드민: 별도 도메인 아님. MEMBER의 ROLE_ADMIN.

## 주의

* BE 스택: Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21(toolchain). Gradle 9는 Boot 3.4 미지원이므로 래퍼 올리지 말 것.
* 테스트 DB는 H2(`testRuntimeOnly`)만 존재. 운영 DB(MySQL) 데이터소스 설정은 아직 없음 → 전역 구성/도메인 작업 시 `application.yaml`에 추가 필요.
* 도메인 문서 없이 해당 도메인 구현 금지. 현재 문서화된 도메인: COMMON, MEMBER.

## 다음 작업

* TODO-READY.md 참고.
