package com.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전역 공통 에러 코드. 코드 문자열({@link #getCode()})은 enum 상수 이름을 그대로 사용한다.
 * {@code resultCode}는 {@code HTTP상태-일련번호} 형식의 문자열 코드로, Swagger/Postman 문서화와 클라이언트 식별에 사용한다.
 * (예: 400 계열 400-01, 405 계열 405-01, 500 계열 500-01)
 * 도메인별 에러 코드는 각 도메인에서 별도 enum으로 확장하거나 여기에 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR("500-01", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE("400-01", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED("405-01", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // --- 인증/인가 ---
    UNAUTHORIZED("401-01", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    MALFORMED_TOKEN("401-02", HttpStatus.UNAUTHORIZED, "토큰 형식이 올바르지 않습니다."),
    INVALID_SIGNATURE("401-03", HttpStatus.UNAUTHORIZED, "토큰 서명이 유효하지 않습니다."),
    EXPIRED_TOKEN("401-04", HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    UNSUPPORTED_TOKEN("401-05", HttpStatus.UNAUTHORIZED, "지원하지 않는 토큰입니다."),
    INVALID_TOKEN_TYPE("401-06", HttpStatus.UNAUTHORIZED, "토큰 종류가 올바르지 않습니다."),
    FORBIDDEN("403-01", HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final String resultCode;
    private final HttpStatus status;
    private final String message;

    /** 에러 코드 문자열. enum 상수 이름을 그대로 사용한다. */
    public String getCode() {
        return name();
    }
}
