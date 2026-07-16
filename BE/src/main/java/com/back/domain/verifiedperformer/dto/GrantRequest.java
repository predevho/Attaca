package com.back.domain.verifiedperformer.dto;

import jakarta.validation.constraints.NotNull;

/** 어드민 직접지정 요청. {@code reason}은 선택. */
public record GrantRequest(
        @NotNull(message = "회원 id는 필수입니다.") Long memberId,
        String reason) {
}
