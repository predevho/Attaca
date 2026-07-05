# TODO-DONE

완료된 작업 기록.

---

* [x] (2026-07-05) BE 전역(global) 기반 구성 (TDD)
  * `global.common.ApiResponse<T>` (성공/실패 공통 래퍼, nested `ErrorBody` record)
  * `global.common.BaseEntity` (`createdAt`/`updatedAt`, `@MappedSuperclass` + JPA Auditing)
  * `global.exception.ErrorCode`(enum, code=상수명), `BusinessException`, `GlobalExceptionHandler`(`@RestControllerAdvice`)
  * `global.config.JpaAuditingConfig` (`@EnableJpaAuditing`)
  * 테스트 4종(ApiResponse/BusinessException/GlobalExceptionHandler/BaseEntity) 및 전체 `clean build` 통과
* [x] (2026-07-05) `BE/build.gradle.kts` Spring Boot 4.1.0 → 3.4.5 재조정, 빌드 통과 확인
  * 4.x 전용 스타터 이름 수정: `-webmvc`→`-web`, 테스트 스타터 3종→`-test`+`spring-security-test`
  * Gradle 래퍼 9.5.1 → 8.11.1 (Boot 3.4 플러그인은 Gradle 9 미지원)
  * 테스트 컨텍스트 로딩용 H2(`testRuntimeOnly`) 추가 → `./gradlew clean build` BUILD SUCCESSFUL
* [x] (2026-07-05) 프로젝트 초기 설계 브레인스토밍 및 문서 체계 작성
  * ARCHITECTURE-CONSTITUTION / STATUTE
  * DOMAIN-COMMON-CONSTITUTION / STATUTE
  * DOMAIN-MEMBER-CONSTITUTION / STATUTE
  * TODO-*, CONTEXT, AI 기록 문서 초기화
