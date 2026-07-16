package com.back.domain.verifiedperformer.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 회원용 인증 연주자 신청 API. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class VerifiedPerformerControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String bearer;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(
                Member.createLocal("performer", "pw", "performer@attaca.com", "연주자"));
        bearer = "Bearer " + jwtProvider.createAccessToken(member.getId(), Role.USER);
    }

    private static final String BODY = "{\"statement\": \"10년 경력\", \"evidenceUrls\": [\"https://a.com/1\"]}";

    @Test
    void 토큰_없이_신청은_401() throws Exception {
        mockMvc.perform(post("/api/verified-performers/applications")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-01"));
    }

    @Test
    void 신청하면_PENDING으로_생성된다() throws Exception {
        mockMvc.perform(post("/api/verified-performers/applications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 중복_신청은_409() throws Exception {
        mockMvc.perform(post("/api/verified-performers/applications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/verified-performers/applications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.resultCode").value("409-04"));
    }

    @Test
    void 지원사유_누락은_400() throws Exception {
        mockMvc.perform(post("/api/verified-performers/applications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\": \"\", \"evidenceUrls\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 신청_이력이_없으면_내_신청_조회는_200_null() throws Exception {
        mockMvc.perform(get("/api/verified-performers/applications/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 신청_후_내_신청_조회는_최신_상태를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/verified-performers/applications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/verified-performers/applications/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
