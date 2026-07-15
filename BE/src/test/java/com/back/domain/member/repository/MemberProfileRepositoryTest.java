package com.back.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.MemberProfile;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class MemberProfileRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberProfileRepository memberProfileRepository;

    private Member savedMember(String suffix) {
        return memberRepository.save(
                Member.createLocal("user" + suffix, "pw", suffix + "@attaca.com", "닉" + suffix));
    }

    @Test
    void memberId로_조회하고_악기_컬렉션이_왕복된다() {
        Member member = savedMember("a");
        MemberProfile profile = MemberProfile.create(member);
        profile.updateInfo(Set.of(Instrument.VIOLIN, Instrument.VOICE), "소개글");
        memberProfileRepository.saveAndFlush(profile);

        assertThat(memberProfileRepository.findByMemberId(member.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.getInstruments())
                            .containsExactlyInAnyOrder(Instrument.VIOLIN, Instrument.VOICE);
                    assertThat(found.getBio()).isEqualTo("소개글");
                });

        assertThat(memberProfileRepository.findByMemberId(999999L)).isEmpty();
    }

    @Test
    void 같은_회원의_프로필은_중복_저장할_수_없다() {
        Member member = savedMember("b");
        memberProfileRepository.saveAndFlush(MemberProfile.create(member));

        assertThatThrownBy(() ->
                memberProfileRepository.saveAndFlush(MemberProfile.create(member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
