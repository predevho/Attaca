package com.back.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void authErrorCodes_haveExpectedResultCodeAndStatus() {
        assertThat(ErrorCode.UNAUTHORIZED.getResultCode()).isEqualTo("401-01");
        assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(ErrorCode.MALFORMED_TOKEN.getResultCode()).isEqualTo("401-02");
        assertThat(ErrorCode.INVALID_SIGNATURE.getResultCode()).isEqualTo("401-03");
        assertThat(ErrorCode.EXPIRED_TOKEN.getResultCode()).isEqualTo("401-04");
        assertThat(ErrorCode.UNSUPPORTED_TOKEN.getResultCode()).isEqualTo("401-05");
        assertThat(ErrorCode.INVALID_TOKEN_TYPE.getResultCode()).isEqualTo("401-06");

        assertThat(ErrorCode.FORBIDDEN.getResultCode()).isEqualTo("403-01");
        assertThat(ErrorCode.FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberErrorCodes_haveExpectedResultCodeAndStatus() {
        assertThat(ErrorCode.EMAIL_ALREADY_EXISTS.getResultCode()).isEqualTo("409-01");
        assertThat(ErrorCode.EMAIL_ALREADY_EXISTS.getStatus()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ErrorCode.NICKNAME_ALREADY_EXISTS.getResultCode()).isEqualTo("409-02");
        assertThat(ErrorCode.NICKNAME_ALREADY_EXISTS.getStatus()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ErrorCode.LOGIN_FAILED.getResultCode()).isEqualTo("401-07");
        assertThat(ErrorCode.LOGIN_FAILED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void oauthAndLoginIdErrorCodes_haveExpectedResultCodeAndStatus() {
        assertThat(ErrorCode.LOGIN_ID_ALREADY_EXISTS.getResultCode()).isEqualTo("409-03");
        assertThat(ErrorCode.LOGIN_ID_ALREADY_EXISTS.getStatus()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ErrorCode.OAUTH_EMAIL_UNVERIFIED.getResultCode()).isEqualTo("401-08");
        assertThat(ErrorCode.OAUTH_EMAIL_UNVERIFIED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(ErrorCode.OAUTH_PROVIDER_ERROR.getResultCode()).isEqualTo("502-01");
        assertThat(ErrorCode.OAUTH_PROVIDER_ERROR.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 파일_에러코드는_지정된_resultCode와_상태를_가진다() {
        assertThat(ErrorCode.INVALID_FILE.getResultCode()).isEqualTo("400-02");
        assertThat(ErrorCode.INVALID_FILE.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_FILE.getCode()).isEqualTo("INVALID_FILE");

        assertThat(ErrorCode.FILE_NOT_FOUND.getResultCode()).isEqualTo("404-01");
        assertThat(ErrorCode.FILE_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(ErrorCode.FILE_UPLOAD_FAILED.getResultCode()).isEqualTo("500-02");
        assertThat(ErrorCode.FILE_UPLOAD_FAILED.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
