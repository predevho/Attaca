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
    RESOURCE_NOT_FOUND("404-02", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // --- 인증/인가 ---
    UNAUTHORIZED("401-01", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    MALFORMED_TOKEN("401-02", HttpStatus.UNAUTHORIZED, "토큰 형식이 올바르지 않습니다."),
    INVALID_SIGNATURE("401-03", HttpStatus.UNAUTHORIZED, "토큰 서명이 유효하지 않습니다."),
    EXPIRED_TOKEN("401-04", HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    UNSUPPORTED_TOKEN("401-05", HttpStatus.UNAUTHORIZED, "지원하지 않는 토큰입니다."),
    INVALID_TOKEN_TYPE("401-06", HttpStatus.UNAUTHORIZED, "토큰 종류가 올바르지 않습니다."),
    LOGIN_FAILED("401-07", HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN("403-01", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // --- MEMBER ---
    EMAIL_ALREADY_EXISTS("409-01", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS("409-02", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    LOGIN_ID_ALREADY_EXISTS("409-03", HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    MEMBER_NOT_FOUND("404-03", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

    // --- OAuth(소셜) ---
    OAUTH_EMAIL_UNVERIFIED("401-08", HttpStatus.UNAUTHORIZED, "소셜 계정의 이메일이 확인되지 않았습니다."),
    OAUTH_PROVIDER_ERROR("502-01", HttpStatus.BAD_GATEWAY, "소셜 로그인 제공자 연동에 실패했습니다."),

    // --- VERIFIED-PERFORMER(인증 연주자) ---
    VERIFICATION_ALREADY_PENDING("409-04", HttpStatus.CONFLICT, "이미 심사 대기 중인 신청이 있습니다."),
    VERIFICATION_ALREADY_APPROVED("409-05", HttpStatus.CONFLICT, "이미 인증된 회원입니다."),
    INVALID_APPLICATION_STATE("409-06", HttpStatus.CONFLICT, "이미 종결된 신청은 다시 처리할 수 없습니다."),
    APPLICATION_NOT_FOUND("404-04", HttpStatus.NOT_FOUND, "인증 신청을 찾을 수 없습니다."),

    // --- 파일 저장 ---
    INVALID_FILE("400-02", HttpStatus.BAD_REQUEST, "올바르지 않은 파일입니다."),
    FILE_NOT_FOUND("404-01", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_UPLOAD_FAILED("500-02", HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");

    private final String resultCode;
    private final HttpStatus status;
    private final String message;

    /** 에러 코드 문자열. enum 상수 이름을 그대로 사용한다. */
    public String getCode() {
        return name();
    }
}
