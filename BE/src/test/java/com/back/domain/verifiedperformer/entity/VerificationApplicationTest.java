package com.back.domain.verifiedperformer.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationApplicationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ADMIN_ID = 99L;

    @Test
    void apply는_PENDING_신청을_만든다() {
        VerificationApplication application =
                VerificationApplication.apply(MEMBER_ID, "10년 경력입니다", List.of("https://a.com/1"));

        assertThat(application.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(application.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(application.getStatement()).isEqualTo("10년 경력입니다");
        assertThat(application.getEvidenceUrls()).containsExactly("https://a.com/1");
        assertThat(application.getDecidedBy()).isNull();
        assertThat(application.getDecidedAt()).isNull();
        assertThat(application.getDecisionReason()).isNull();
    }

    @Test
    void grantByAdmin은_신청서_없이_APPROVED를_만든다() {
        VerificationApplication application =
                VerificationApplication.grantByAdmin(MEMBER_ID, ADMIN_ID, "직접 지정");

        assertThat(application.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(application.getStatement()).isNull();
        assertThat(application.getEvidenceUrls()).isEmpty();
        assertThat(application.getDecidedBy()).isEqualTo(ADMIN_ID);
        assertThat(application.getDecisionReason()).isEqualTo("직접 지정");
        assertThat(application.getDecidedAt()).isNotNull();
    }

    @Test
    void approve는_PENDING을_APPROVED로_전이한다() {
        VerificationApplication application = pending();

        application.approve(ADMIN_ID, "승인 사유");

        assertThat(application.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(application.getDecidedBy()).isEqualTo(ADMIN_ID);
        assertThat(application.getDecisionReason()).isEqualTo("승인 사유");
        assertThat(application.getDecidedAt()).isNotNull();
    }

    @Test
    void reject는_PENDING을_REJECTED로_전이한다() {
        VerificationApplication application = pending();

        application.reject(ADMIN_ID, "증빙 부족");

        assertThat(application.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(application.getDecidedBy()).isEqualTo(ADMIN_ID);
        assertThat(application.getDecisionReason()).isEqualTo("증빙 부족");
        assertThat(application.getDecidedAt()).isNotNull();
    }

    @Test
    void revoke는_APPROVED를_REVOKED로_전이한다() {
        VerificationApplication application = pending();
        application.approve(ADMIN_ID, "승인");

        application.revoke(ADMIN_ID, "허위 판명");

        assertThat(application.getStatus()).isEqualTo(VerificationStatus.REVOKED);
        assertThat(application.getDecisionReason()).isEqualTo("허위 판명");
    }

    @Test
    void 종결된_신청은_다시_승인할_수_없다() {
        VerificationApplication application = pending();
        application.reject(ADMIN_ID, "거절");

        assertThatThrownBy(() -> application.approve(ADMIN_ID, "다시 승인"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_APPLICATION_STATE);
    }

    @Test
    void PENDING이_아니면_거절할_수_없다() {
        VerificationApplication application = pending();
        application.approve(ADMIN_ID, "승인");

        assertThatThrownBy(() -> application.reject(ADMIN_ID, "거절"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_APPLICATION_STATE);
    }

    @Test
    void APPROVED가_아니면_철회할_수_없다() {
        VerificationApplication application = pending();

        assertThatThrownBy(() -> application.revoke(ADMIN_ID, "철회"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_APPLICATION_STATE);
    }

    private VerificationApplication pending() {
        return VerificationApplication.apply(MEMBER_ID, "사유", List.of("https://a.com/1"));
    }
}
