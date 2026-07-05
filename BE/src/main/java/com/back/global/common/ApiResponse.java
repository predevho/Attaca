package com.back.global.common;

import com.back.global.exception.ErrorCode;
import lombok.Getter;

/**
 * 모든 API 응답의 공통 래퍼.
 *
 * <pre>
 * 성공: { "success": true,  "data": {...}, "error": null }
 * 실패: { "success": false, "data": null,  "error": { "resultCode": 40001, "code": "...", "message": "..." } }
 * </pre>
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorBody error;

    private ApiResponse(boolean success, T data, ErrorBody error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        ErrorBody body = new ErrorBody(errorCode.getResultCode(), errorCode.getCode(), message);
        return new ApiResponse<>(false, null, body);
    }

    public record ErrorBody(int resultCode, String code, String message) {
    }
}
