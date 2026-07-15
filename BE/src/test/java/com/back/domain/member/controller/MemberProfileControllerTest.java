package com.back.domain.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class MemberProfileControllerTest {

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
                Member.createLocal("profileuser", "pw", "profile@attaca.com", "프로필유저"));
        bearer = "Bearer " + jwtProvider.createAccessToken(member.getId(), Role.USER);
    }

    @Test
    void 토큰_없이_프로필_조회는_401() throws Exception {
        mockMvc.perform(get("/api/members/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-01"));
    }

    @Test
    void 프로필이_없으면_빈_기본값_200() throws Exception {
        mockMvc.perform(get("/api/members/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.instruments").isEmpty())
                .andExpect(jsonPath("$.data.bio").isEmpty())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
    }

    @Test
    void 프로필을_수정하면_조회에_반영된다() throws Exception {
        mockMvc.perform(put("/api/members/me/profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\": [\"VIOLIN\", \"VOICE\"], \"bio\": \"안녕하세요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("안녕하세요"));

        mockMvc.perform(get("/api/members/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instruments[0]").value("VIOLIN"))
                .andExpect(jsonPath("$.data.instruments[1]").value("VOICE"));
    }

    @Test
    void 없는_악기_코드는_400() throws Exception {
        mockMvc.perform(put("/api/members/me/profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\": [\"VIOLINN\"], \"bio\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 자기소개_500자_초과는_400() throws Exception {
        String longBio = "가".repeat(501);
        mockMvc.perform(put("/api/members/me/profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\": [], \"bio\": \"" + longBio + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 이미지를_업로드하면_url을_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "얼굴.png", "image/png",
                "img".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/members/me/profile/image")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isNotEmpty());
    }

    @Test
    void 이미지가_아닌_파일은_400() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/members/me/profile/image")
                        .file(pdf)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-02"));
    }

    @Test
    void 선택지_목록은_악기_21종을_담는다() throws Exception {
        mockMvc.perform(get("/api/members/profile-options").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instruments.length()").value(Instrument.values().length))
                .andExpect(jsonPath("$.data.instruments[0].code").value("VIOLIN"))
                .andExpect(jsonPath("$.data.instruments[0].label").value("바이올린"));
    }
}
