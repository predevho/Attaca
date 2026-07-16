package com.back.domain.verifiedperformer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 거절/철회 요청. 사유는 필수(어드민 기록·통보 근거). */
public record DecisionReasonRequest(
        @NotBlank(message = "처리 사유를 입력해 주세요.")
        @Size(max = 500, message = "처리 사유는 500자를 넘을 수 없습니다.") String reason) {
}
