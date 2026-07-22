package com.back.domain.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.recruitment.dto.RecruitmentPostingRequest;
import com.back.domain.recruitment.dto.RecruitmentPostingResponse;
import com.back.domain.recruitment.dto.RecruitmentScope;
import com.back.domain.recruitment.repository.RecruitmentPostingRepository;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class RecruitmentPostingServiceTest {

    @Autowired RecruitmentPostingRepository postingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private RecruitmentPostingService service;
    private Long authorId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        MemberQueryService memberQueryService = new MemberQueryService(memberRepository,
                new VerifiedPerformerService(verificationApplicationRepository));
        service = new RecruitmentPostingService(postingRepository, memberQueryService);

        authorId = memberRepository.save(Member.createLocal("author", "pw", "a@x.com", "작성자"))
                .getId();
        otherId = memberRepository.save(Member.createLocal("other", "pw", "o@x.com", "타인")).getId();
    }

    private RecruitmentPostingRequest request(LocalDateTime deadline) {
        return new RecruitmentPostingRequest("첼로 구함", "설명", Set.of(Instrument.CELLO), 2,
                "서울", "회당 5만원", deadline);
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 인증회원_누구나_등록할_수_있고_작성자정보가_실린다() {
        RecruitmentPostingResponse res = service.register(authorId,
                request(LocalDateTime.now().plusDays(7)));

        assertThat(res.title()).isEqualTo("첼로 구함");
        assertThat(res.author().nickname()).isEqualTo("작성자");
        assertThat(res.instruments()).containsExactly(Instrument.CELLO);
        assertThat(res.closed()).isFalse();
    }

    @Test
    void 삭제된_공고_조회는_RECRUITMENT_NOT_FOUND() {
        RecruitmentPostingResponse created = service.register(authorId,
                request(LocalDateTime.now().plusDays(7)));
        service.deletePosting(authorId, false, created.id());

        assertThatThrownBy(() -> service.getPosting(created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND));
    }

    @Test
    void scope_OPEN은_모집중만_보인다() {
        service.register(authorId, request(LocalDateTime.now().plusDays(1)));
        RecruitmentPostingResponse closed = service.register(authorId,
                request(LocalDateTime.now().plusDays(1)));
        service.closePosting(authorId, closed.id());

        assertThat(service.getPostings(RecruitmentScope.OPEN, null, PageRequest.of(0, 10))
                .getContent()).hasSize(1);
        assertThat(service.getPostings(RecruitmentScope.ALL, null, PageRequest.of(0, 10))
                .getContent()).hasSize(2);
    }

    @Test
    void 작성자가_아니면_수정_마감은_FORBIDDEN() {
        RecruitmentPostingResponse created = service.register(authorId,
                request(LocalDateTime.now().plusDays(7)));

        assertThatThrownBy(() -> service.editPosting(otherId, created.id(),
                request(LocalDateTime.now().plusDays(7))))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.closePosting(otherId, created.id()))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 타인_삭제는_FORBIDDEN이지만_어드민은_가능하다() {
        RecruitmentPostingResponse created = service.register(authorId,
                request(LocalDateTime.now().plusDays(7)));

        assertThatThrownBy(() -> service.deletePosting(otherId, false, created.id()))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deletePosting(otherId, true, created.id()); // 어드민 모더레이션
        assertThat(postingRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
    }
}
