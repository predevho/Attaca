package com.back.domain.recruitment.controller;

import com.back.domain.recruitment.dto.ApplyRecruitmentRequest;
import com.back.domain.recruitment.dto.RecruitmentApplicationResponse;
import com.back.domain.recruitment.service.RecruitmentApplicationService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구인 공고 지원 API. 모두 인증 필요(principal = memberId).
 * 지원=인증 회원(본인 공고 제외), 지원자 목록·수락·거절=공고 작성자, 철회=지원자 본인.
 */
@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentApplicationController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final RecruitmentApplicationService applicationService;

    @PostMapping("/{id}/applications")
    public ApiResponse<RecruitmentApplicationResponse> apply(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id,
            @Valid @RequestBody ApplyRecruitmentRequest request) {
        return ApiResponse.success(applicationService.apply(memberId, id, request));
    }

    @GetMapping("/{id}/applications")
    public ApiResponse<Page<RecruitmentApplicationResponse>> listForPosting(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(applicationService.getApplicationsForPosting(memberId, id,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @GetMapping("/applications/me")
    public ApiResponse<Page<RecruitmentApplicationResponse>> listMine(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(applicationService.getMyApplications(memberId,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @PostMapping("/applications/{applicationId}/accept")
    public ApiResponse<RecruitmentApplicationResponse> accept(
            @AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.accept(memberId, applicationId));
    }

    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<RecruitmentApplicationResponse> reject(
            @AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.reject(memberId, applicationId));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public ApiResponse<RecruitmentApplicationResponse> withdraw(
            @AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.withdraw(memberId, applicationId));
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
