package com.back.domain.performance.dto;

import com.back.domain.member.dto.MemberDisplay;
import java.time.LocalDateTime;

public record PerformanceResponse(
        Long id,
        MemberDisplay organizer,
        String title,
        String description,
        LocalDateTime performedAt,
        String venue,
        String program,
        String ticketInfo,
        String ticketUrl,
        String posterImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
