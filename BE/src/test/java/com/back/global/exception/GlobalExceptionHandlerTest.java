package com.back.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessException_mapsToErrorCodeStatusAndWrapper() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.error.message").value("커스텀 메시지"));
    }

    @Test
    void uncaughtException_mapsToInternalServerError() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.resultCode").value("500-01"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void noResourceFoundException_mapsToResourceNotFound_notFileNotFound() throws Exception {
        // /files/** 가 아닌, 매칭되는 핸들러가 없는 일반 라우팅 경로를 검증한다.
        // 파일 저장소 예외(FILE_NOT_FOUND, 404-01)가 아닌 일반 리소스 미발견(RESOURCE_NOT_FOUND, 404-02)이어야 한다.
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.resultCode").value("404-02"))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        public void business() {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "커스텀 메시지");
        }

        @GetMapping("/test/boom")
        public void boom() {
            throw new RuntimeException("unexpected");
        }

        @GetMapping("/test/no-resource")
        public void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/no-resource");
        }
    }
}
