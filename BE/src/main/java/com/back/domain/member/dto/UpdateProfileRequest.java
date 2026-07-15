package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 프로필 전체 교체 요청(PUT). {@code instruments}가 null이면 빈 목록으로 간주한다. */
public record UpdateProfileRequest(
        @Size(max = 10, message = "악기는 최대 10개까지 선택할 수 있습니다.") List<Instrument> instruments,
        @Size(max = 500, message = "자기소개는 500자를 넘을 수 없습니다.") String bio) {
}
