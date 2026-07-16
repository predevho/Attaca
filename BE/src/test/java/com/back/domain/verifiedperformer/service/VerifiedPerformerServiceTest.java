package com.back.domain.verifiedperformer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.verifiedperformer.dto.ApplicationResponse;
import com.back.domain.verifiedperformer.dto.ApplyRequest;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.entity.VerificationApplication;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class VerifiedPerformerServiceTest {

    private static final Long MEMBER = 1L;
    private static final Long ADMIN = 99L;

    @Autowired
    private VerificationApplicationRepository repository;

    private VerifiedPerformerService service;

    @BeforeEach
    void setUp() {
        service = new VerifiedPerformerService(repository);
    }

    private ApplyRequest applyRequest() {
        return new ApplyRequest("10년 경력입니다", List.of("https://a.com/proof"));
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    // --- 신청 ---

    @Test
    void 신청하면_PENDING_레코드가_생성된다() {
        ApplicationResponse response = service.apply(MEMBER, applyRequest());

        assertThat(response.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.memberId()).isEqualTo(MEMBER);
        assertThat(repository.existsByMemberIdAndStatus(MEMBER, VerificationStatus.PENDING)).isTrue();
    }

    @Test
    void 심사대기_신청이_있으면_재신청은_거절된다() {
        service.apply(MEMBER, applyRequest());

        assertThatThrownBy(() -> service.apply(MEMBER, applyRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.VERIFICATION_ALREADY_PENDING));
    }

    @Test
    void 이미_인증된_회원의_재신청은_거절된다() {
        ApplicationResponse pending = service.apply(MEMBER, applyRequest());
        service.approve(pending.id(), ADMIN, "승인");

        assertThatThrownBy(() -> service.apply(MEMBER, applyRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.VERIFICATION_ALREADY_APPROVED));
    }

    @Test
    void 거절_이력만_있으면_재신청이_가능하고_이력은_보존된다() {
        ApplicationResponse first = service.apply(MEMBER, applyRequest());
        service.reject(first.id(), ADMIN, "증빙 부족");

        ApplicationResponse second = service.apply(MEMBER, applyRequest());

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(repository.count()).isEqualTo(2); // 기존 레코드 보존
    }

    // --- 심사 전이 ---

    @Test
    void 승인하면_APPROVED가_된다() {
        ApplicationResponse pending = service.apply(MEMBER, applyRequest());

        ApplicationResponse approved = service.approve(pending.id(), ADMIN, "확인 완료");

        assertThat(approved.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(approved.decisionReason()).isEqualTo("확인 완료");
    }

    @Test
    void 거절하면_REJECTED가_된다() {
        ApplicationResponse pending = service.apply(MEMBER, applyRequest());

        ApplicationResponse rejected = service.reject(pending.id(), ADMIN, "부족");

        assertThat(rejected.status()).isEqualTo(VerificationStatus.REJECTED);
    }

    @Test
    void 철회하면_REVOKED가_된다() {
        ApplicationResponse pending = service.apply(MEMBER, applyRequest());
        service.approve(pending.id(), ADMIN, "승인");

        ApplicationResponse revoked = service.revoke(pending.id(), ADMIN, "허위");

        assertThat(revoked.status()).isEqualTo(VerificationStatus.REVOKED);
    }

    @Test
    void 종결된_신청의_재처리는_INVALID_APPLICATION_STATE() {
        ApplicationResponse pending = service.apply(MEMBER, applyRequest());
        service.reject(pending.id(), ADMIN, "거절");

        assertThatThrownBy(() -> service.approve(pending.id(), ADMIN, "다시"))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.INVALID_APPLICATION_STATE));
    }

    @Test
    void 없는_신청의_처리는_APPLICATION_NOT_FOUND() {
        assertThatThrownBy(() -> service.approve(404404L, ADMIN, "승인"))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND));
    }

    // --- 어드민 직접지정 ---

    @Test
    void 직접지정은_신청서_없이_APPROVED를_만든다() {
        ApplicationResponse granted = service.grant(new GrantRequest(MEMBER, "우수 연주자"), ADMIN);

        assertThat(granted.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(granted.statement()).isNull();
        assertThat(granted.evidenceUrls()).isEmpty();
        assertThat(service.isVerified(MEMBER)).isTrue();
    }

    @Test
    void 활성_신청이_있으면_직접지정은_거절된다() {
        service.apply(MEMBER, applyRequest());

        assertThatThrownBy(() -> service.grant(new GrantRequest(MEMBER, "지정"), ADMIN))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.VERIFICATION_ALREADY_PENDING));
    }

    // --- 뱃지 판정 ---

    @Test
    void isVerified는_APPROVED만_true다() {
        assertThat(service.isVerified(MEMBER)).isFalse(); // 이력 없음

        ApplicationResponse pending = service.apply(MEMBER, applyRequest());
        assertThat(service.isVerified(MEMBER)).isFalse(); // PENDING

        service.approve(pending.id(), ADMIN, "승인");
        assertThat(service.isVerified(MEMBER)).isTrue(); // APPROVED

        service.revoke(pending.id(), ADMIN, "철회");
        assertThat(service.isVerified(MEMBER)).isFalse(); // REVOKED
    }

    // --- 조회 ---

    @Test
    void 내_최신_신청을_조회한다() {
        service.apply(MEMBER, applyRequest());

        ApplicationResponse latest = service.getMyLatestApplication(MEMBER);

        assertThat(latest).isNotNull();
        assertThat(latest.status()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void 신청_이력이_없으면_null을_돌려준다() {
        assertThat(service.getMyLatestApplication(MEMBER)).isNull();
    }

    @Test
    void 어드민은_상태별_신청_목록을_페이징으로_조회한다() {
        service.apply(1L, applyRequest());
        service.apply(2L, applyRequest());

        List<ApplicationResponse> pending = service
                .getApplications(VerificationStatus.PENDING, PageRequest.of(0, 10))
                .getContent();

        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(a -> a.status() == VerificationStatus.PENDING);
    }
}
