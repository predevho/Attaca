package com.back.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.dto.OAuthLoginRequest;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.oauth.KakaoOAuthClient;
import com.back.domain.member.oauth.OAuthUserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberAuthControllerOAuthTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        when(kakaoOAuthClient.provider()).thenReturn(OAuthProvider.KAKAO);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void kakaoLogin_newVerifiedUser_returns200WithTokens() throws Exception {
        when(kakaoOAuthClient.fetch(any(), any()))
                .thenReturn(new OAuthUserInfo("kakao-1", "new@attaca.com", true, "카카오유저"));

        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OAuthLoginRequest("auth-code", "https://app/cb"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void kakaoLogin_unverifiedEmail_returns401() throws Exception {
        when(kakaoOAuthClient.fetch(any(), any()))
                .thenReturn(new OAuthUserInfo("kakao-2", "x@attaca.com", false, "미검증"));

        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OAuthLoginRequest("auth-code", "https://app/cb"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-08"));
    }
}
