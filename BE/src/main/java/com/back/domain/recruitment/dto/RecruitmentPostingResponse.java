package com.back.domain.recruitment.dto;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import java.time.LocalDateTime;
import java.util.Set;

public record RecruitmentPostingResponse(
        Long id,
        MemberDisplay author,
        String title,
        String description,
        Set<Instrument> instruments,
        Integer recruitCount,
        String location,
        String fee,
        LocalDateTime deadline,
        RecruitmentStatus status,
        boolean closed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RecruitmentPostingResponse of(RecruitmentPosting p, MemberDisplay author,
            LocalDateTime now) {
        return new RecruitmentPostingResponse(p.getId(), author, p.getTitle(), p.getDescription(),
                Set.copyOf(p.getInstruments()), p.getRecruitCount(), p.getLocation(), p.getFee(),
                p.getDeadline(), p.getStatus(), p.isClosed(now), p.getCreatedAt(), p.getUpdatedAt());
    }
}
