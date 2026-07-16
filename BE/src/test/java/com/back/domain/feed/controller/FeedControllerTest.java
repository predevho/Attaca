package com.back.domain.feed.controller;

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
class FeedControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;

    private String authorBearer;
    private Long authorId;
    private String otherBearer;
    private String adminBearer;

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.createLocal("w", "pw", "w@x.com", "글쓴이"));
        Member other = memberRepository.save(Member.createLocal("o", "pw", "o@x.com", "남"));
        authorId = author.getId();
        authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), Role.USER);
        otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), Role.USER);
        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
    }

    private String createPost() throws Exception {
        String json = mockMvc.perform(post("/api/feed/posts").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"글\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 토큰_없이_작성은_401() throws Exception {
        mockMvc.perform(post("/api/feed/posts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"글\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게시글_작성과_타임라인_조회() throws Exception {
        createPost();
        mockMvc.perform(get("/api/feed/posts").header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("글"))
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("글쓴이"));
    }

    @Test
    void 빈_내용_작성은_400() throws Exception {
        mockMvc.perform(post("/api/feed/posts").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 타인_게시글_수정은_403() throws Exception {
        String id = createPost();
        mockMvc.perform(put("/api/feed/posts/" + id).header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"해킹\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 어드민은_타인_게시글을_삭제할_수_있다() throws Exception {
        String id = createPost();
        mockMvc.perform(delete("/api/feed/posts/" + id).header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/feed/posts/" + id).header("Authorization", authorBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-05"));
    }

    @Test
    void 댓글_작성과_좋아요() throws Exception {
        String id = createPost();
        mockMvc.perform(post("/api/feed/posts/" + id + "/comments")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("댓글"));

        mockMvc.perform(post("/api/feed/posts/" + id + "/like").header("Authorization", otherBearer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/posts/" + id).header("Authorization", otherBearer))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true));
    }
}
