package com.back.domain.member.dto;

/** 다른 도메인(FEED 등)에 노출할 작성자 표시정보. 닉네임과 인증 뱃지. */
public record MemberDisplay(Long memberId, String nickname, boolean verified) {
}
