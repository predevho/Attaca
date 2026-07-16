package com.back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class MemberQueryServiceTest {

    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private MemberQueryService service;
    private VerifiedPerformerService verifiedPerformerService;

    @BeforeEach
    void setUp() {
        verifiedPerformerService = new VerifiedPerformerService(verificationApplicationRepository);
        service = new MemberQueryService(memberRepository, verifiedPerformerService);
    }

    @Test
    void 회원_표시정보를_배치로_돌려주고_인증뱃지를_채운다() {
        Member a = memberRepository.save(Member.createLocal("a", "pw", "a@x.com", "가"));
        Member b = memberRepository.save(Member.createLocal("b", "pw", "b@x.com", "나"));
        verifiedPerformerService.grant(new GrantRequest(a.getId(), "지정"), 99L); // a 인증

        Map<Long, MemberDisplay> map = service.findDisplaysByIds(Set.of(a.getId(), b.getId()));

        assertThat(map.get(a.getId()).nickname()).isEqualTo("가");
        assertThat(map.get(a.getId()).verified()).isTrue();
        assertThat(map.get(b.getId()).verified()).isFalse();
    }

    @Test
    void 빈_입력은_빈_맵() {
        assertThat(service.findDisplaysByIds(Set.of())).isEmpty();
    }
}
