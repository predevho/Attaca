package com.back.domain.verifiedperformer.controller;

import com.back.domain.verifiedperformer.dto.ApplicationResponse;
import com.back.domain.verifiedperformer.dto.DecisionReasonRequest;
import com.back.domain.verifiedperformer.dto.DecisionRequest;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민용 인증 연주자 심사 API. 경로 {@code /api/admin/**} 는 SecurityConfig 에서 ROLE_ADMIN 전용.
 * principal(어드민 memberId)은 처리자 기록(decidedBy)에 남는다.
 */
@RestController
@RequestMapping("/api/admin/verified-performers")
@RequiredArgsConstructor
public class VerifiedPerformerAdminController {

    private final VerifiedPerformerService verifiedPerformerService;

    @GetMapping("/applications")
    public ApiResponse<Page<ApplicationResponse>> applications(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status, Pageable pageable) {
        return ApiResponse.success(verifiedPerformerService.getApplications(status, pageable));
    }

    @PostMapping("/applications/{id}/approve")
    public ApiResponse<ApplicationResponse> approve(@AuthenticationPrincipal Long adminId,
            @PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(verifiedPerformerService.approve(id, adminId, reason));
    }

    @PostMapping("/applications/{id}/reject")
    public ApiResponse<ApplicationResponse> reject(@AuthenticationPrincipal Long adminId,
            @PathVariable Long id, @Valid @RequestBody DecisionReasonRequest request) {
        return ApiResponse.success(verifiedPerformerService.reject(id, adminId, request.reason()));
    }

    @PostMapping("/applications/{id}/revoke")
    public ApiResponse<ApplicationResponse> revoke(@AuthenticationPrincipal Long adminId,
            @PathVariable Long id, @Valid @RequestBody DecisionReasonRequest request) {
        return ApiResponse.success(verifiedPerformerService.revoke(id, adminId, request.reason()));
    }

    @PostMapping("/grant")
    public ApiResponse<ApplicationResponse> grant(@AuthenticationPrincipal Long adminId,
            @Valid @RequestBody GrantRequest request) {
        return ApiResponse.success(verifiedPerformerService.grant(request, adminId));
    }
}
