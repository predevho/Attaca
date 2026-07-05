package com.back.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 전역 공통 에러 코드. 코드 문자열은 enum 상수 이름을 그대로 사용한다.
 * 도메인별 에러 코드는 각 도메인에서 별도 enum으로 확장하거나 여기에 추가한다.
 */
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }
}
