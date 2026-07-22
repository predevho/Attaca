package com.back.domain.recruitment.dto;

import com.back.domain.member.entity.Instrument;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

/** 공고 등록/수정 공용 요청(전체 교체). */
public record RecruitmentPostingRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.") String title,
        @Size(max = 2000, message = "설명은 2000자를 넘을 수 없습니다.") String description,
        @NotEmpty(message = "모집 파트를 하나 이상 선택해 주세요.") Set<Instrument> instruments,
        @Min(value = 1, message = "모집 인원은 1명 이상이어야 합니다.") Integer recruitCount,
        @Size(max = 200, message = "활동 지역/장소는 200자를 넘을 수 없습니다.") String location,
        @Size(max = 200, message = "보수 안내는 200자를 넘을 수 없습니다.") String fee,
        LocalDateTime deadline) {
}
