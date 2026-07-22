package com.back.domain.recruitment.dto;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import java.time.LocalDateTime;

public record RecruitmentApplicationResponse(
        Long id,
        Long postingId,
        MemberDisplay applicant,
        String message,
        RecruitmentApplicationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RecruitmentApplicationResponse of(RecruitmentApplication a,
            MemberDisplay applicant) {
        return new RecruitmentApplicationResponse(a.getId(), a.getPostingId(), applicant,
                a.getMessage(), a.getStatus(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
