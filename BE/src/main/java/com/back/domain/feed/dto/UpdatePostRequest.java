package com.back.domain.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "게시글은 2000자를 넘을 수 없습니다.") String content) {
}
