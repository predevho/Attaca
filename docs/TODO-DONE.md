# TODO-DONE

완료된 작업 기록.

---

* [x] (2026-07-14) 파일 저장 기반(FileStorage) 구성 (TDD)
  * `global.storage`: `FileStorage`(인터페이스) / `LocalFileStorage`(기본) / `S3FileStorage`(AWS SDK v2) / `StorageProperties`
  * `FileService.upload(MultipartFile, String directory, Long uploaderId)` — key 생성(`{디렉터리}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}`) + 저장 + `FileMetadata` 영속화를 한 트랜잭션으로 조합
  * `FileStorage`는 DB를 모른다(책임 분리). 도메인은 `FileService`만 사용
  * 접근 URL = `base-url + key` → CloudFront/R2 전환 시 설정 한 줄만 교체
  * 에러코드 4종 추가: `INVALID_FILE`(400-02), `FILE_NOT_FOUND`(404-01), `FILE_UPLOAD_FAILED`(500-02), `RESOURCE_NOT_FOUND`(404-02, 파일 전용 아닌 일반 코드). `BusinessException`에 cause 보존 생성자 추가
  * `SecurityConfig`에 `/files/**` permit 추가(로컬 파일 서빙)
  * 문서 충돌 해소: 메타데이터는 공용 `FileMetadata` 테이블로 결정 → `DOMAIN-COMMON-STATUTE §7` 개정
  * 리뷰 중 발견·수정한 버그 2건:
    1. 앱 전역 500→404 라우팅 버그 — 매칭되는 핸들러가 없는 모든 URL에서 Spring이 던지는 `NoResourceFoundException`이 `GlobalExceptionHandler`의 catch-all(`Exception.class`)에 걸려 500으로 응답되고 있었음. `RESOURCE_NOT_FOUND`(404-02) 전용 핸들러를 추가해 404로 정정(파일 저장 기능과 무관한 앱 전역 수정).
    2. key 생성의 확장자 파서가 dotfile(예: `.내파일`, 점이 index 0)에서 원본 파일명 전체를 key로 흘려보내던 버그 — "원본 파일명은 key에 넣지 않는다" 규칙을 깨고 한글이 공개 URL에 노출될 수 있었음. 점이 index 0이면 확장자 없음으로 처리하도록 수정.
  * 범위 밖: HTTP 업로드 엔드포인트(사용처인 MEMBER 프로필 이미지에서 구현), 실제 S3 연동 검증(자격증명 미발급), Presigned URL
* [x] (2026-07-13) MEMBER: 카카오 소셜 로그인 + 자체 로그인 loginId 전환 (TDD, subagent-driven)
  * 식별자 역할 분리: `loginId`(자체 로그인, unique·nullable) / `email`(인증·소셜연결 키, 전원 필수) / `nickname`(활동명) / 내부 신원 `id`
  * 자체 auth를 email→loginId 기반으로 개정(`SignupRequest{loginId,password,email,nickname}`, `login{loginId,password}`)
  * 카카오 소셜: `POST /api/auth/oauth/kakao{code,redirectUri}` — 프론트 인가코드→백엔드 교환(`OAuthClient`/`KakaoOAuthClient`), `SocialAccount`(provider+providerUserId 유니크)
  * 자동가입/자동연결: 검증된 이메일(is_email_verified)만 기존 회원 연결, 미검증 거절(계정 탈취 방지). nickname 충돌 시 유니크 생성
  * 에러코드 추가: `LOGIN_ID_ALREADY_EXISTS`(409-03), `OAUTH_EMAIL_UNVERIFIED`(401-08), `OAUTH_PROVIDER_ERROR`(502-01). `LOGIN_FAILED` 문구를 아이디 기준으로 정정
  * 카카오 키는 env 주입(`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`), 커밋 금지. 실제 카카오 HTTP는 운영 키로 수동 검증(자동 테스트는 매핑·로직만 Fake)
  * 브랜치 `feature/member-oauth2-social-login`, 태스크 6개 TDD + 태스크별 리뷰, 전체 `clean build` 통과
  * 범위 밖(BACKLOG): 구글 등 타 provider, 프로필/이미지, 실제 인증메일 발송
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
