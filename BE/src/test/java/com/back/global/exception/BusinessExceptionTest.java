package com.back.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void carriesErrorCodeAndDefaultMessage() {
        BusinessException ex = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    }

    @Test
    void allowsCustomMessage() {
        BusinessException ex =
                new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이메일 형식이 올바르지 않습니다.");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(ex.getMessage()).isEqualTo("이메일 형식이 올바르지 않습니다.");
    }
}
