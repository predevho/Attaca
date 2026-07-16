package com.back.domain.feed.dto;

import com.back.domain.member.dto.MemberDisplay;
import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        MemberDisplay author,
        String content,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
