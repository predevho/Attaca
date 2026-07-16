package com.back.domain.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 500, message = "댓글은 500자를 넘을 수 없습니다.") String content) {
}
