package com.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전역 공통 에러 코드. 코드 문자열({@link #getCode()})은 enum 상수 이름을 그대로 사용한다.
 * {@code resultCode}는 HTTP 상태 기반 숫자 코드로, Swagger/Postman 문서화와 클라이언트 식별에 사용한다.
 * (예: 400 계열 40001, 405 계열 40501, 500 계열 50001)
 * 도메인별 에러 코드는 각 도메인에서 별도 enum으로 확장하거나 여기에 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(40001, HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(40501, HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");

    private final int resultCode;
    private final HttpStatus status;
    private final String message;

    /** 에러 코드 문자열. enum 상수 이름을 그대로 사용한다. */
    public String getCode() {
        return name();
    }
}
