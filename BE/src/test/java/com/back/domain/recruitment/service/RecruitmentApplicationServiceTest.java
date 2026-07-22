package com.back.domain.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.recruitment.dto.ApplyRecruitmentRequest;
import com.back.domain.recruitment.dto.RecruitmentApplicationResponse;
import com.back.domain.recruitment.dto.RecruitmentPostingRequest;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import com.back.domain.recruitment.repository.RecruitmentApplicationRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class RecruitmentApplicationServiceTest {

    @Autowired RecruitmentPostingRepository postingRepository;
    @Autowired RecruitmentApplicationRepository applicationRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private RecruitmentPostingService postingService;
    private RecruitmentApplicationService service;
    private Long authorId;
    private Long applicantId;

    @BeforeEach
    void setUp() {
        MemberQueryService memberQueryService = new MemberQueryService(memberRepository,
                new VerifiedPerformerService(verificationApplicationRepository));
        postingService = new RecruitmentPostingService(postingRepository, memberQueryService);
        service = new RecruitmentApplicationService(applicationRepository, postingService,
                memberQueryService);

        authorId = memberRepository.save(Member.createLocal("author", "pw", "a@x.com", "작성자"))
                .getId();
        applicantId = memberRepository.save(Member.createLocal("app", "pw", "p@x.com", "지원자"))
                .getId();
    }

    private Long openPosting(LocalDateTime deadline) {
        return postingService.register(authorId, new RecruitmentPostingRequest("첼로 구함", "설명",
                Set.of(Instrument.CELLO), 1, "서울", "무보수", deadline)).id();
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 지원하면_PENDING이고_지원자정보가_실린다() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));

        RecruitmentApplicationResponse res = service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("첼로 지원합니다"));

        assertThat(res.status()).isEqualTo(RecruitmentApplicationStatus.PENDING);
        assertThat(res.applicant().nickname()).isEqualTo("지원자");
    }

    @Test
    void 본인공고_지원은_CANNOT_APPLY_OWN_RECRUITMENT() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));

        assertThatThrownBy(() -> service.apply(authorId, postingId,
                new ApplyRecruitmentRequest("m")))
                .satisfies(t -> assertThat(errorOf(t))
                        .isEqualTo(ErrorCode.CANNOT_APPLY_OWN_RECRUITMENT));
    }

    @Test
    void 마감된_공고_지원은_RECRUITMENT_CLOSED() {
        Long postingId = openPosting(LocalDateTime.now().minusDays(1)); // 마감일 지남

        assertThatThrownBy(() -> service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("m")))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.RECRUITMENT_CLOSED));
    }

    @Test
    void 중복_지원은_ALREADY_APPLIED() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));
        service.apply(applicantId, postingId, new ApplyRecruitmentRequest("m1"));

        assertThatThrownBy(() -> service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("m2")))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.ALREADY_APPLIED));
    }

    @Test
    void 작성자는_수락하고_타인은_지원자목록조회_FORBIDDEN() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));
        RecruitmentApplicationResponse applied = service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("m"));

        RecruitmentApplicationResponse accepted = service.accept(authorId, applied.id());
        assertThat(accepted.status()).isEqualTo(RecruitmentApplicationStatus.ACCEPTED);

        assertThatThrownBy(() -> service.getApplicationsForPosting(applicantId, postingId,
                PageRequest.of(0, 10)))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 지원자는_PENDING을_철회하고_작성자철회는_FORBIDDEN() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));
        RecruitmentApplicationResponse applied = service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("m"));

        assertThatThrownBy(() -> service.withdraw(authorId, applied.id()))
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        RecruitmentApplicationResponse withdrawn = service.withdraw(applicantId, applied.id());
        assertThat(withdrawn.status()).isEqualTo(RecruitmentApplicationStatus.WITHDRAWN);
    }

    @Test
    void 거절뒤_재지원은_허용된다() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));
        RecruitmentApplicationResponse applied = service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("m1"));
        service.reject(authorId, applied.id());

        RecruitmentApplicationResponse reapplied = service.apply(applicantId, postingId,
                new ApplyRecruitmentRequest("m2"));
        assertThat(reapplied.status()).isEqualTo(RecruitmentApplicationStatus.PENDING);
    }

    @Test
    void 작성자는_여러_지원자목록을_조회하고_지원자정보가_모두_채워진다() {
        Long postingId = openPosting(LocalDateTime.now().plusDays(7));
        Long applicant2Id = memberRepository
                .save(Member.createLocal("app2", "pw", "p2@x.com", "지원자2")).getId();

        service.apply(applicantId, postingId, new ApplyRecruitmentRequest("m1"));
        service.apply(applicant2Id, postingId, new ApplyRecruitmentRequest("m2"));

        Page<RecruitmentApplicationResponse> page = service.getApplicationsForPosting(authorId,
                postingId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(res -> {
            assertThat(res.applicant()).isNotNull();
            assertThat(res.applicant().nickname()).isIn("지원자", "지원자2");
        });
    }

    @Test
    void 지원자는_본인의_지원목록을_전체조회한다() {
        Long postingId1 = openPosting(LocalDateTime.now().plusDays(7));
        Long postingId2 = openPosting(LocalDateTime.now().plusDays(7));

        service.apply(applicantId, postingId1, new ApplyRecruitmentRequest("m1"));
        service.apply(applicantId, postingId2, new ApplyRecruitmentRequest("m2"));

        Page<RecruitmentApplicationResponse> page = service.getMyApplications(applicantId,
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(res ->
                assertThat(res.applicant().nickname()).isEqualTo("지원자"));
    }
}
