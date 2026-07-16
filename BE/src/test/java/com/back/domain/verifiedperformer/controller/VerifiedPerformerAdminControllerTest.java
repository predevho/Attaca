package com.back.domain.verifiedperformer.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.dto.ApplyRequest;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 어드민용 인증 연주자 심사 API. 권한(ROLE_ADMIN)과 심사 전이를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class VerifiedPerformerAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private VerifiedPerformerService service;

    private Long memberId;
    private String adminBearer;
    private String userBearer;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(
                Member.createLocal("applicant", "pw", "applicant@attaca.com", "신청자"));
        memberId = member.getId();
        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
        userBearer = "Bearer " + jwtProvider.createAccessToken(memberId, Role.USER);
    }

    private Long newPendingApplicationId() {
        return service.apply(memberId, new ApplyRequest("사유", List.of("https://a.com/1"))).id();
    }

    @Test
    void 일반_회원은_어드민_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/verified-performers/applications")
                        .header("Authorization", userBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 어드민은_상태별_신청_목록을_조회한다() throws Exception {
        newPendingApplicationId();

        mockMvc.perform(get("/api/admin/verified-performers/applications")
                        .param("status", "PENDING")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    void 어드민이_승인하면_APPROVED가_된다() throws Exception {
        Long id = newPendingApplicationId();

        mockMvc.perform(post("/api/admin/verified-performers/applications/" + id + "/approve")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"확인\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void 승인은_사유_없이도_가능하다() throws Exception {
        Long id = newPendingApplicationId();

        mockMvc.perform(post("/api/admin/verified-performers/applications/" + id + "/approve")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void 거절은_사유가_필수다() throws Exception {
        Long id = newPendingApplicationId();

        mockMvc.perform(post("/api/admin/verified-performers/applications/" + id + "/reject")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 없는_신청_승인은_404() throws Exception {
        mockMvc.perform(post("/api/admin/verified-performers/applications/999999/approve")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"확인\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-04"));
    }

    @Test
    void 어드민_직접지정은_APPROVED를_만든다() throws Exception {
        mockMvc.perform(post("/api/admin/verified-performers/grant")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": " + memberId + ", \"reason\": \"우수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 직접지정으로 인증 상태가 반영된다.
        org.assertj.core.api.Assertions.assertThat(service.isVerified(memberId)).isTrue();
    }

    @Test
    void 어드민이_철회하면_REVOKED가_된다() throws Exception {
        Long id = newPendingApplicationId();
        service.approve(id, 9999L, "승인");

        mockMvc.perform(post("/api/admin/verified-performers/applications/" + id + "/revoke")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"허위\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(VerificationStatus.REVOKED.name()));
    }
}
