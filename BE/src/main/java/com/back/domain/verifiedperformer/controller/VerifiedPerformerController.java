package com.back.domain.verifiedperformer.controller;

import com.back.domain.verifiedperformer.dto.ApplicationResponse;
import com.back.domain.verifiedperformer.dto.ApplyRequest;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원용 인증 연주자 신청 API. 모두 인증 필요 — principal 은 JWT 의 회원 id(Long).
 * 심사(승인/거절/철회/직접지정)는 어드민 API({@code /api/admin/verified-performers/**})에 있다.
 */
@RestController
@RequestMapping("/api/verified-performers")
@RequiredArgsConstructor
public class VerifiedPerformerController {

    private final VerifiedPerformerService verifiedPerformerService;

    @PostMapping("/applications")
    public ApiResponse<ApplicationResponse> apply(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ApplyRequest request) {
        return ApiResponse.success(verifiedPerformerService.apply(memberId, request));
    }

    /** 내 최신 신청 상태. 신청 이력이 없으면 data 는 null. */
    @GetMapping("/applications/me")
    public ApiResponse<ApplicationResponse> myLatestApplication(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(verifiedPerformerService.getMyLatestApplication(memberId));
    }
}
