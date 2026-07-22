package com.back.domain.recruitment.controller;

import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.dto.RecruitmentPostingRequest;
import com.back.domain.recruitment.dto.RecruitmentPostingResponse;
import com.back.domain.recruitment.dto.RecruitmentScope;
import com.back.domain.recruitment.service.RecruitmentPostingService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구인 공고 API. 모두 인증 필요(principal = memberId).
 * 등록은 인증 회원 누구나, 모더레이션 삭제는 역할로 판정 — 어드민 전용 경로 없음.
 */
@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentPostingController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final RecruitmentPostingService postingService;

    @PostMapping
    public ApiResponse<RecruitmentPostingResponse> register(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RecruitmentPostingRequest request) {
        return ApiResponse.success(postingService.register(memberId, request));
    }

    @GetMapping
    public ApiResponse<Page<RecruitmentPostingResponse>> list(
            @RequestParam(defaultValue = "OPEN") RecruitmentScope scope,
            @RequestParam(required = false) Instrument instrument,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(postingService.getPostings(scope, instrument,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecruitmentPostingResponse> get(@PathVariable Long id) {
        return ApiResponse.success(postingService.getPosting(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecruitmentPostingResponse> edit(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id, @Valid @RequestBody RecruitmentPostingRequest request) {
        return ApiResponse.success(postingService.editPosting(memberId, id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<RecruitmentPostingResponse> close(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(postingService.closePosting(memberId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        postingService.deletePosting(memberId, isAdmin(authentication), id);
        return ApiResponse.success();
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
