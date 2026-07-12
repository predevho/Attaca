# TODO-DONE

완료된 작업 기록.

---

* [x] (2026-07-12) MEMBER 도메인: 자체 회원가입/로그인 (TDD)
  * `domain.member`: `Member`(엔티티, email/password/nickname/role), `MemberRepository`(existsByEmail·existsByNickname·findByEmail), `MemberService`(signup·login), `MemberAuthController`
  * 엔드포인트: `POST /api/auth/signup`, `POST /api/auth/login`(access+refresh 발급) — 기존 `/api/auth/**` permit 재사용(SecurityConfig 미변경)
  * 에러코드 전역 `ErrorCode`에 추가: `LOGIN_FAILED`(401-07), `EMAIL_ALREADY_EXISTS`(409-01), `NICKNAME_ALREADY_EXISTS`(409-02)
  * 비밀번호 BCrypt 해시 저장, 로그인 실패는 이메일/비번 구분 없이 401-07(정보 노출 방지)
  * 테스트: Member/Repository/Service(6)/Controller(4)/ErrorCode + 회귀 수정(SecurityConfigTest `@WebMvcTest` 범위 한정) → 전체 `clean build` 통과(43개)
  * 범위 밖(BACKLOG): 소셜 로그인(OAuth2), 프로필/이미지(FileStorage 의존)
* [x] (2026-07-11) BE 보안 기반(Security+JWT) 골격 구성 (TDD, 접근안 A)
  * `global.security`: `Role`(enum), `JwtProvider`/`JwtProperties`, `JwtAuthenticationFilter`, `SecurityConfig`(STATELESS), 핸들러 2종, `AuthController(/api/auth/reissue)`
  * access+refresh 무상태(refresh에도 role), 인증 ErrorCode 7종(401-01~06/403-01), jjwt 0.12.6
  * 테스트 5종(Role/ErrorCode/JwtProvider/Filter/SecurityConfig/AuthController) + 전체 `clean build` 통과
  * 커밋 `5a76833`·`184e19a`·`90b4427`·`287379d`·`6da73cf`
* [x] (2026-07-05) 전역 코드 리뷰 반영: `ErrorCode.resultCode`(int, HTTP 기반) 추가 + `ErrorBody(resultCode, code, message)` 응답 노출, Lombok `@Getter` 전환 (TDD, `clean build` 통과)
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
