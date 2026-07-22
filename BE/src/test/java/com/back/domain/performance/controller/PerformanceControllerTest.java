package com.back.domain.performance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
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
class PerformanceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired VerifiedPerformerService verifiedPerformerService;

    private String verifiedBearer;
    private String normalBearer;
    private String adminBearer;

    private static final String BODY = "{\"title\":\"리사이틀\",\"description\":\"소개\","
            + "\"performedAt\":\"2026-12-01T19:30:00\",\"venue\":\"예술의전당\","
            + "\"program\":\"베토벤\",\"ticketInfo\":\"3만원\",\"ticketUrl\":\"https://t.example/1\"}";

    @BeforeEach
    void setUp() {
        Member verified = memberRepository.save(Member.createLocal("perf", "pw", "perf@x.com", "연주자"));
        verifiedPerformerService.grant(new GrantRequest(verified.getId(), "지정"), 99L);
        verifiedBearer = "Bearer " + jwtProvider.createAccessToken(verified.getId(), Role.USER);

        Member normal = memberRepository.save(Member.createLocal("norm", "pw", "norm@x.com", "일반"));
        normalBearer = "Bearer " + jwtProvider.createAccessToken(normal.getId(), Role.USER);

        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
    }

    private String register(String bearer) throws Exception {
        String json = mockMvc.perform(post("/api/performances").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 토큰_없이_등록은_401() throws Exception {
        mockMvc.perform(post("/api/performances")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증연주자는_등록하고_목록에_보인다() throws Exception {
        register(verifiedBearer);
        mockMvc.perform(get("/api/performances").param("scope", "UPCOMING")
                        .header("Authorization", verifiedBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("리사이틀"))
                .andExpect(jsonPath("$.data.content[0].organizer.nickname").value("연주자"));
    }

    @Test
    void 일반회원_등록은_403_NOT_VERIFIED_PERFORMER() throws Exception {
        mockMvc.perform(post("/api/performances").header("Authorization", normalBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-02"));
    }

    @Test
    void 어드민은_등록할_수_있다() throws Exception {
        mockMvc.perform(post("/api/performances").header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("리사이틀"));
    }

    @Test
    void 공연명_누락은_400() throws Exception {
        mockMvc.perform(post("/api/performances").header("Authorization", verifiedBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"performedAt\":\"2026-12-01T19:30:00\",\"venue\":\"홀\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 타인_수정은_403() throws Exception {
        String id = register(verifiedBearer);
        mockMvc.perform(put("/api/performances/" + id).header("Authorization", normalBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 어드민은_타인_공연을_삭제할_수_있다() throws Exception {
        String id = register(verifiedBearer);
        mockMvc.perform(delete("/api/performances/" + id).header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/performances/" + id).header("Authorization", verifiedBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-07"));
    }

    @Test
    void 주최자는_포스터를_올린다() throws Exception {
        String id = register(verifiedBearer);
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png",
                "img".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/performances/" + id + "/poster")
                        .file(file).header("Authorization", verifiedBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posterImageUrl").isNotEmpty());
    }
}
