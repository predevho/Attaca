package com.back.domain.recruitment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class RecruitmentPostingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;

    private String authorBearer;
    private String otherBearer;
    private String adminBearer;

    private static final String BODY = "{\"title\":\"첼로 구함\",\"description\":\"설명\","
            + "\"instruments\":[\"CELLO\",\"VIOLA\"],\"recruitCount\":2,\"location\":\"서울\","
            + "\"fee\":\"회당 5만원\",\"deadline\":\"2026-12-01T19:30:00\"}";

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.createLocal("author", "pw", "a@x.com", "작성자"));
        authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), Role.USER);
        Member other = memberRepository.save(Member.createLocal("other", "pw", "o@x.com", "타인"));
        otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), Role.USER);
        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
    }

    private String register(String bearer) throws Exception {
        String json = mockMvc.perform(post("/api/recruitments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 토큰_없이_등록은_401() throws Exception {
        mockMvc.perform(post("/api/recruitments")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증회원은_등록하고_목록에_보인다() throws Exception {
        register(authorBearer);
        mockMvc.perform(get("/api/recruitments").param("scope", "OPEN")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("첼로 구함"))
                .andExpect(jsonPath("$.data.content[0].author.nickname").value("작성자"))
                .andExpect(jsonPath("$.data.content[0].closed").value(false));
    }

    @Test
    void 악기필터로_목록을_거른다() throws Exception {
        register(authorBearer);
        mockMvc.perform(get("/api/recruitments").param("scope", "OPEN").param("instrument", "VIOLIN")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void 모집파트_누락은_400() throws Exception {
        mockMvc.perform(post("/api/recruitments").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"instruments\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 타인_수정은_403() throws Exception {
        String id = register(authorBearer);
        mockMvc.perform(put("/api/recruitments/" + id).header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 작성자는_마감할_수_있다() throws Exception {
        String id = register(authorBearer);
        mockMvc.perform(post("/api/recruitments/" + id + "/close")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closed").value(true));
    }

    @Test
    void 어드민은_타인_공고를_삭제할_수_있다() throws Exception {
        String id = register(authorBearer);
        mockMvc.perform(delete("/api/recruitments/" + id).header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recruitments/" + id).header("Authorization", authorBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-08"));
    }
}
