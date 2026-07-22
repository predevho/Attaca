package com.back.domain.recruitment.controller;

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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RecruitmentApplicationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;

    private String authorBearer;
    private String applicantBearer;

    private static final String POSTING_BODY = "{\"title\":\"첼로 구함\",\"description\":\"설명\","
            + "\"instruments\":[\"CELLO\"],\"recruitCount\":1,\"deadline\":\"2026-12-01T19:30:00\"}";
    private static final String APPLY_BODY = "{\"message\":\"첼로 지원합니다\"}";

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.createLocal("author", "pw", "a@x.com", "작성자"));
        authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), Role.USER);
        Member applicant = memberRepository.save(
                Member.createLocal("app", "pw", "p@x.com", "지원자"));
        applicantBearer = "Bearer " + jwtProvider.createAccessToken(applicant.getId(), Role.USER);
    }

    private String createPosting() throws Exception {
        String json = mockMvc.perform(post("/api/recruitments").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(POSTING_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    private String apply(String postingId) throws Exception {
        String json = mockMvc.perform(post("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", applicantBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 지원하면_PENDING으로_생성된다() throws Exception {
        String postingId = createPosting();
        mockMvc.perform(post("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", applicantBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.applicant.nickname").value("지원자"));
    }

    @Test
    void 본인공고_지원은_409() throws Exception {
        String postingId = createPosting();
        mockMvc.perform(post("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.resultCode").value("409-09"));
    }

    @Test
    void 작성자는_지원자목록을_보고_수락한다() throws Exception {
        String postingId = createPosting();
        String applicationId = apply(postingId);

        mockMvc.perform(get("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].message").value("첼로 지원합니다"));

        mockMvc.perform(post("/api/recruitments/applications/" + applicationId + "/accept")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void 지원자는_내지원목록을_보고_철회한다() throws Exception {
        String postingId = createPosting();
        String applicationId = apply(postingId);

        mockMvc.perform(get("/api/recruitments/applications/me")
                        .header("Authorization", applicantBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));

        mockMvc.perform(post("/api/recruitments/applications/" + applicationId + "/withdraw")
                        .header("Authorization", applicantBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void 타인이_지원자목록조회는_403() throws Exception {
        String postingId = createPosting();
        apply(postingId);

        mockMvc.perform(get("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", applicantBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }
}
