package com.back.global.security.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.security.Role;
import com.back.global.security.auth.controller.AuthController;
import com.back.global.security.auth.dto.ReissueRequest;
import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final JwtProperties props = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256-0123456789", 1800000L, 1209600000L);
    private final JwtProvider jwtProvider = new JwtProvider(props);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(new ReissueRequest(refreshToken));
    }

    @Test
    void validRefresh_returnsNewAccessToken() throws Exception {
        String refresh = jwtProvider.createRefreshToken(1L, Role.USER);

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void accessTokenInsteadOfRefresh_returns401TypeError() throws Exception {
        String access = jwtProvider.createAccessToken(1L, Role.USER);

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(access)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-06"));
    }

    @Test
    void expiredRefresh_returns401Expired() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(props.secret(), -1000L, -1000L));
        String refresh = expiredProvider.createRefreshToken(1L, Role.USER);

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-04"));
    }
}
