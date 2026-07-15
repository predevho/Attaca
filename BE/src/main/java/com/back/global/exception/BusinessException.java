package com.back.global.exception;

import lombok.Getter;

/**
 * 비즈니스 예외의 공통 상위 타입. 항상 {@link ErrorCode}를 가진다.
 * 도메인 예외는 이 예외를 상속하거나 ErrorCode를 사용해 던진다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
