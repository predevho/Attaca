package com.back.domain.feed.dto;

import com.back.domain.member.dto.MemberDisplay;
import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        MemberDisplay author,
        String content,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt) {
}
