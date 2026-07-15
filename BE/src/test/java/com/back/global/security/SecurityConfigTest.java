package com.back.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.global.security.handler.JwtAccessDeniedHandler;
import com.back.global.security.handler.JwtAuthenticationEntryPoint;
import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import com.back.global.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 인가 규칙 자체를 검증하는 슬라이스 테스트. 실제 도메인 컨트롤러(서비스/JPA 의존)를 끌어오지 않도록
// 이 테스트 전용 TestController 로 스캔 범위를 한정한다.
@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        SecurityConfigTest.TestBeans.class,
        SecurityConfigTest.TestController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.resultCode").value("401-01"));
    }

    @Test
    void validUserToken_returns200() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.USER);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userToken_onAdminPath_returns403() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.USER);
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void adminToken_onAdminPath_returns200() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.ADMIN);
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tamperedToken_returns401WithSignatureCode() throws Exception {
        JwtProvider other = new JwtProvider(new JwtProperties(
                "another-secret-key-long-enough-for-hs256-9876543210", 1800000L, 1209600000L));
        String token = other.createAccessToken(1L, Role.USER);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-03"));
    }

    @Test
    void 파일_경로는_토큰_없이_접근_가능하다() throws Exception {
        // 실제 저장 디렉터리에 해당 파일이 없으므로 404가 정상이다.
        // 핵심은 401(인증 요구)이 아니라는 것 — 인가 규칙에서 permit 되었는지만 검증한다.
        mockMvc.perform(get("/files/profile/2026/07/14/abc.png"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-key-that-is-long-enough-for-hs256-0123456789",
                    1800000L, 1209600000L);
        }

        @Bean
        JwtProvider jwtProvider(JwtProperties props) {
            return new JwtProvider(props);
        }

        // LocalFileServingConfig 는 WebMvcConfigurer 구현체라 @WebMvcTest 슬라이스가
        // 자동으로 감지해 빈으로 만든다. 그 생성자가 요구하는 StorageProperties 를
        // 여기서 제공하지 않으면 컨텍스트 로딩 자체가 실패한다(존재하지 않는 경로라도 무방).
        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("local",
                    new StorageProperties.Local("build/test-files", "http://localhost:8080/files"),
                    null);
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/api/me")
        public String me() {
            return "me";
        }

        @GetMapping("/api/admin/ping")
        public String adminPing() {
            return "pong";
        }
    }
}
