package com.back.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void success_withData_setsSuccessTrueAndData() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getError()).isNull();
    }

    @Test
    void success_withoutData_setsSuccessTrueAndNullData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void error_fromErrorCode_setsSuccessFalseAndErrorBody() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().resultCode()).isEqualTo("500-01");
        assertThat(response.getError().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getError().message())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    @Test
    void error_withCustomMessage_overridesDefaultMessage() {
        ApiResponse<Void> response =
                ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, "이메일 형식이 올바르지 않습니다.");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().resultCode()).isEqualTo("400-01");
        assertThat(response.getError().code()).isEqualTo("INVALID_INPUT_VALUE");
        assertThat(response.getError().message()).isEqualTo("이메일 형식이 올바르지 않습니다.");
    }
}
