# DOMAIN-COMMON-STATUTE

공통 도메인 규칙. 모든 도메인이 따르는 구체적 구현 규칙을 정의한다.

---

## 1. API 응답 포맷

* 일관된 응답 래퍼를 사용한다.

```
{
  "success": true | false,
  "data": { ... } | null,
  "error": { "resultCode": 40001, "code": "INVALID_INPUT_VALUE", "message": "..." } | null
}
```

* 성공 시 `success = true`, `data`에 결과, `error = null`.
* 실패 시 `success = false`, `data = null`, `error`에 `resultCode`/`code`/`message`.
* 구현: `com.back.global.common.ApiResponse<T>` (정적 팩토리 `success`/`success(data)`/`error`). `error`는 nested record `ErrorBody(resultCode, code, message)`.
* `error.code` 문자열은 `ErrorCode` enum 상수 이름을 그대로 사용한다 (예: `INVALID_INPUT_VALUE`).
* `error.resultCode`(int)는 HTTP 상태 기반 숫자 코드다. `ErrorCode`에 정의하며 Swagger/Postman 문서화·클라이언트 식별에 사용한다.
  * 규칙: `HTTP 상태(3자리) + 일련번호(2자리)`. 예) 400 계열 `40001`, 405 계열 `40501`, 500 계열 `50001`.

---

## 2. 예외 처리

* 위치: `com.back.global.exception`
* 구성:
  * `BusinessException` : 비즈니스 예외의 공통 상위 타입. 에러 코드를 갖는다.
  * `ErrorCode` (enum) : 에러 코드와 기본 메시지, HTTP 상태를 정의한다.
  * `GlobalExceptionHandler` (`@RestControllerAdvice`) : 예외를 응답 래퍼로 일괄 변환한다.
* 도메인은 자기 예외를 `BusinessException`을 상속하거나 `ErrorCode`를 사용해 던진다.
* `ErrorCode`는 `(resultCode:int, status:HttpStatus, message:String)`를 갖는다. `getCode()`는 enum 상수명을 반환한다.

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
* JWT 필터에서 토큰을 검증하고 SecurityContext에 인증 정보를 넣는다.
* URL/메서드 단위로 권한(`ROLE_USER`, `ROLE_ADMIN`)을 검사한다.
* OAuth2 로그인 성공 시 회원을 조회/생성하고 JWT를 발급한다.

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
