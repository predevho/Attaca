package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.MemberProfile;
import java.util.Comparator;
import java.util.List;

/**
 * 내 프로필 응답. {@code instruments}는 이름순 정렬로 응답 순서를 고정한다.
 * {@code verified}는 이 도메인의 값이 아니라 VERIFIED-PERFORMER 도메인이 제공하는 파생 뱃지다
 * (DOMAIN-VERIFIED-PERFORMER-STATUTE §4.3).
 */
public record ProfileResponse(List<Instrument> instruments, String bio, String profileImageUrl,
        boolean verified) {

    public static ProfileResponse from(MemberProfile profile, String profileImageUrl,
            boolean verified) {
        List<Instrument> sorted = profile.getInstruments().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return new ProfileResponse(sorted, profile.getBio(), profileImageUrl, verified);
    }

    /** 프로필 미생성 회원용 빈 기본값(404 대신 — FE가 편집 화면을 그리기 쉽다). 뱃지는 별개로 파생될 수 있다. */
    public static ProfileResponse empty(boolean verified) {
        return new ProfileResponse(List.of(), null, null, verified);
    }
}
