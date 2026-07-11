package com.back.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.global.security.handler.JwtAccessDeniedHandler;
import com.back.global.security.handler.JwtAuthenticationEntryPoint;
import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
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
