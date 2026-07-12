# CONTEXT

현재 작업 수행에 필요한 최소 정보만 유지한다. 로그 저장소로 쓰지 않는다.

---

## 현재 상태

* 단계: BE 전역 기반 + 보안 기반(Security+JWT) 골격 + MEMBER 자체 회원가입/로그인 완료. 다음은 MEMBER 소셜 로그인(OAuth2)/프로필 또는 FileStorage/FE 초기화.
* 확정된 기술 스택
  * BE: Spring Boot 3.4.x / Java 21 / MySQL / Spring Security(JWT + OAuth2) / WebSocket(STOMP)+Redis / S3(FileStorage 추상화)
  * FE: Next.js (React), 위치 `FE/` (아직 비어 있음)
* 도메인 6개: MEMBER, VERIFIED-PERFORMER, FEED, PERFORMANCE, RECRUITMENT(구인/구직), CHAT
* 어드민: 별도 도메인 아님. MEMBER의 ROLE_ADMIN.

## 주의

* BE 스택: Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21(toolchain). Gradle 9는 Boot 3.4 미지원이므로 래퍼 올리지 말 것.
* 테스트 DB는 H2(`testRuntimeOnly`)만 존재. 운영 DB(MySQL) 데이터소스 설정은 아직 없음 → 전역 구성/도메인 작업 시 `application.yaml`에 추가 필요.
* 도메인 문서 없이 해당 도메인 구현 금지. 현재 문서화된 도메인: COMMON, MEMBER.
* 코드 스타일: 단순 필드 접근자는 Lombok `@Getter`로 통일(수동 getter 금지).
* 응답 에러 본문은 `ErrorBody(resultCode:String, code:String, message)`. `resultCode`는 `HTTP상태-일련번호` 문자열(400-01/405-01/500-01).
* 보안: 무상태 JWT(access+refresh, `/api/auth/reissue`). `jwt.secret`은 env(`JWT_SECRET`) 주입·커밋 금지. 골격은 DB 불필요. 인증 ErrorCode 401-01~07, 403-01.
* MEMBER: 자체 가입/로그인은 `/api/auth/signup`·`/api/auth/login`(둘 다 `/api/auth/**` permit). 로그인은 access+refresh 발급. 비번 BCrypt. MEMBER 에러코드는 전역 `ErrorCode`에 추가(EMAIL/NICKNAME_ALREADY_EXISTS 409-01/02, LOGIN_FAILED 401-07). `Member.role`은 `global.security.Role` 재사용.

## 보류된 결정

* `ErrorCode`를 인터페이스로 승격해 도메인별 에러 코드(예: `MemberErrorCode`)를 분리할지 여부 → 지금 확정하지 않음. 개발 진행하며 명확해질 때 결정. (현재는 전역 enum 단일 구조)

## 다음 작업

* TODO-READY.md 참고.
