package com.back.domain.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공고 지원 요청. 어느 파트에 지원하는지 등을 자유 기술한다. */
public record ApplyRecruitmentRequest(
        @NotBlank(message = "지원 메시지를 입력해 주세요.")
        @Size(max = 1000, message = "지원 메시지는 1000자를 넘을 수 없습니다.") String message) {
}
