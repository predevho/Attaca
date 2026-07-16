package com.back.domain.verifiedperformer.dto;

import com.back.domain.verifiedperformer.entity.VerificationApplication;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 인증 신청 응답. 내 신청 상태 조회와 어드민 목록에 공통으로 쓴다.
 * 어드민 처리 정보(decisionReason/decidedBy/decidedAt)는 PENDING 이면 null.
 */
public record ApplicationResponse(
        Long id,
        Long memberId,
        String statement,
        List<String> evidenceUrls,
        VerificationStatus status,
        String decisionReason,
        Long decidedBy,
        LocalDateTime decidedAt,
        LocalDateTime createdAt) {

    public static ApplicationResponse from(VerificationApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getMemberId(),
                application.getStatement(),
                List.copyOf(application.getEvidenceUrls()),
                application.getStatus(),
                application.getDecisionReason(),
                application.getDecidedBy(),
                application.getDecidedAt(),
                application.getCreatedAt());
    }
}
