package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.MemberProfile;
import java.util.Comparator;
import java.util.List;

/** 내 프로필 응답. {@code instruments}는 이름순 정렬로 응답 순서를 고정한다. */
public record ProfileResponse(List<Instrument> instruments, String bio, String profileImageUrl) {

    public static ProfileResponse from(MemberProfile profile, String profileImageUrl) {
        List<Instrument> sorted = profile.getInstruments().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return new ProfileResponse(sorted, profile.getBio(), profileImageUrl);
    }

    /** 프로필 미생성 회원용 빈 기본값(404 대신 — FE가 편집 화면을 그리기 쉽다). */
    public static ProfileResponse empty() {
        return new ProfileResponse(List.of(), null, null);
    }
}
