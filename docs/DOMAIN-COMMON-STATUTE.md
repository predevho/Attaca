# DOMAIN-COMMON-STATUTE

공통 도메인 규칙. 모든 도메인이 따르는 구체적 구현 규칙을 정의한다.

---

## 1. API 응답 포맷

* 일관된 응답 래퍼를 사용한다.

```
{
  "success": true | false,
  "data": { ... } | null,
  "error": { "resultCode": "400-01", "code": "INVALID_INPUT_VALUE", "message": "..." } | null
}
```

* 성공 시 `success = true`, `data`에 결과, `error = null`.
* 실패 시 `success = false`, `data = null`, `error`에 `resultCode`/`code`/`message`.
* 구현: `com.back.global.common.ApiResponse<T>` (정적 팩토리 `success`/`success(data)`/`error`). `error`는 nested record `ErrorBody(resultCode, code, message)`.
* `error.code` 문자열은 `ErrorCode` enum 상수 이름을 그대로 사용한다 (예: `INVALID_INPUT_VALUE`).
* `error.resultCode`(String)는 `HTTP상태-일련번호` 형식의 문자열 코드다. `ErrorCode`에 정의하며 Swagger/Postman 문서화·클라이언트 식별에 사용한다.
  * 규칙: `HTTP 상태(3자리)-일련번호(2자리)`. 예) 400 계열 `400-01`, 405 계열 `405-01`, 500 계열 `500-01`.

---

## 2. 예외 처리

* 위치: `com.back.global.exception`
* 구성:
  * `BusinessException` : 비즈니스 예외의 공통 상위 타입. 에러 코드를 갖는다.
  * `ErrorCode` (enum) : 에러 코드와 기본 메시지, HTTP 상태를 정의한다.
  * `GlobalExceptionHandler` (`@RestControllerAdvice`) : 예외를 응답 래퍼로 일괄 변환한다.
* 도메인은 자기 예외를 `BusinessException`을 상속하거나 `ErrorCode`를 사용해 던진다.
* `ErrorCode`는 `(resultCode:String, status:HttpStatus, message:String)`를 갖는다. `getCode()`는 enum 상수명을 반환한다.

---

## 8. Lombok / 코드 스타일

* 단순 필드 접근자는 손으로 작성하지 않고 Lombok `@Getter`로 생성한다. (클래스/enum 단위 부착, 필드만 유지)
* 파생 값(예: `ErrorCode.getCode()` = `name()`)처럼 필드 접근이 아닌 메서드는 명시적으로 작성한다.

---

## 3. 공통 엔티티

* 위치: `com.back.global.common`
* `BaseEntity` : `createdAt`, `updatedAt` 을 갖는 추상 클래스. `@MappedSuperclass` + JPA Auditing 사용.
* 모든 엔티티는 특별한 이유가 없으면 `BaseEntity`를 상속한다.

---

## 4. 인증/인가

* 위치: `com.back.global.security`
* 무상태(STATELESS) JWT 기반. 세션 미사용, `csrf`/`formLogin`/`httpBasic` 비활성.
* 구성:
  * `JwtProvider` : access/refresh 발급·파싱·검증(HS256, self-issued). 무상태 재발급을 위해 refresh 에도 role claim 포함.
  * `JwtProperties` : `jwt.secret`(env 주입), access/refresh 만료(ms).
  * `JwtAuthenticationFilter` : Bearer access 검증 → `SecurityContext` 세팅. 실패 시 사유 ErrorCode 를 request 속성에 저장.
  * `SecurityConfig` : `SecurityFilterChain`(인가 규칙 + 필터·핸들러 등록), `PasswordEncoder`(BCrypt) 빈.
  * `JwtAuthenticationEntryPoint`(401)/`JwtAccessDeniedHandler`(403) : 필터 체인 실패를 `ApiResponse` JSON 으로 직접 응답(전역 핸들러가 못 잡으므로).
  * `Role`(enum USER/ADMIN, `authority()`→`ROLE_x`). MEMBER 의 `Member.role` 이 재사용.
  * `AuthController` `POST /api/auth/reissue` : refresh 검증 후 새 access 재발급(무상태, DB 조회 없음).
* 인가 규칙: `/api/auth/**` permit, `/api/admin/**` `hasRole("ADMIN")`, 그 외 `authenticated()`.
* 인증 계열 ErrorCode:

| 코드 | resultCode | 사유 |
|---|---|---|
| UNAUTHORIZED | 401-01 | 토큰 없음/미인증 |
| MALFORMED_TOKEN | 401-02 | 형식 오류 |
| INVALID_SIGNATURE | 401-03 | 서명 변조 |
| EXPIRED_TOKEN | 401-04 | 만료 |
| UNSUPPORTED_TOKEN | 401-05 | 미지원 |
| INVALID_TOKEN_TYPE | 401-06 | reissue에 access 전달 등 |
| FORBIDDEN | 403-01 | 권한 부족 |

* 소셜 로그인(OAuth2): provider 인증 성공 → 회원 조회/생성 → 동일 `JwtProvider` 발급 흐름으로 수렴 (MEMBER 도메인에서 구현).
* refresh 로테이션·철회는 추후 Redis 도입 시 추가(현재 무상태).

---

## 5. 페이징

* 목록 조회는 Spring `Pageable`을 사용한다.
* 응답은 페이지 정보(전체 개수, 페이지 번호, 크기)를 포함한다.

---

## 6. DTO 규칙

* 요청 DTO와 응답 DTO를 분리한다.
* Entity ↔ DTO 변환은 도메인 내부(서비스 또는 DTO 정적 팩토리)에서 처리한다.
* Entity를 그대로 반환하지 않는다.

---

## 7. 파일 업로드

* 파일 업로드가 필요한 도메인은 `com.back.global.storage.FileStorage`를 통해 저장한다.
* 저장 후 얻은 논리 key와 접근 URL을 도메인 엔티티에 보관한다.
