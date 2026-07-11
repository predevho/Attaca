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
}
