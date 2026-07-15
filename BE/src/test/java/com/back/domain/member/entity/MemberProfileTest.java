package com.back.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberProfileTest {

    private Member member() {
        return Member.createLocal("user1", "encoded-pw", "u1@attaca.com", "유저일");
    }

    @Test
    void 생성_직후에는_빈_프로필이다() {
        MemberProfile profile = MemberProfile.create(member());

        assertThat(profile.getInstruments()).isEmpty();
        assertThat(profile.getBio()).isNull();
        assertThat(profile.getProfileImageKey()).isNull();
    }

    @Test
    void updateInfo는_악기와_소개를_전체_교체한다() {
        MemberProfile profile = MemberProfile.create(member());
        profile.updateInfo(Set.of(Instrument.VIOLIN, Instrument.PIANO), "첫 소개");

        profile.updateInfo(Set.of(Instrument.CELLO), "수정된 소개");

        assertThat(profile.getInstruments()).containsExactly(Instrument.CELLO);
        assertThat(profile.getBio()).isEqualTo("수정된 소개");
    }

    @Test
    void changeImage는_key를_교체한다() {
        MemberProfile profile = MemberProfile.create(member());

        profile.changeImage("profile/2026/07/15/a.png");

        assertThat(profile.getProfileImageKey()).isEqualTo("profile/2026/07/15/a.png");
    }

    @Test
    void 악기는_21종이고_모두_한글_label을_가진다() {
        assertThat(Instrument.values()).hasSize(21);
        assertThat(Instrument.VIOLIN.getLabel()).isEqualTo("바이올린");
        assertThat(Instrument.VOICE.getLabel()).isEqualTo("성악");
        assertThat(Instrument.VOCAL.getLabel()).isEqualTo("보컬");
        for (Instrument instrument : Instrument.values()) {
            assertThat(instrument.getLabel()).isNotBlank();
        }
    }
}
