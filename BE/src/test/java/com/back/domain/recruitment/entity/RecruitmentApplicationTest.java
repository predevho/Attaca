package com.back.domain.recruitment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class RecruitmentApplicationTest {

    @Test
    void 지원하면_PENDING으로_시작한다() {
        RecruitmentApplication a = RecruitmentApplication.apply(10L, 20L, "첼로 파트 지원합니다");

        assertThat(a.getStatus()).isEqualTo(RecruitmentApplicationStatus.PENDING);
        assertThat(a.getPostingId()).isEqualTo(10L);
        assertThat(a.getApplicantId()).isEqualTo(20L);
        assertThat(a.getMessage()).isEqualTo("첼로 파트 지원합니다");
    }

    @Test
    void accept_reject_withdraw는_PENDING에서만_전이된다() {
        RecruitmentApplication accepted = RecruitmentApplication.apply(10L, 20L, "m");
        accepted.accept();
        assertThat(accepted.getStatus()).isEqualTo(RecruitmentApplicationStatus.ACCEPTED);

        RecruitmentApplication rejected = RecruitmentApplication.apply(10L, 21L, "m");
        rejected.reject();
        assertThat(rejected.getStatus()).isEqualTo(RecruitmentApplicationStatus.REJECTED);

        RecruitmentApplication withdrawn = RecruitmentApplication.apply(10L, 22L, "m");
        withdrawn.withdraw();
        assertThat(withdrawn.getStatus()).isEqualTo(RecruitmentApplicationStatus.WITHDRAWN);
    }

    @Test
    void 이미_처리된_지원을_다시_처리하면_예외() {
        RecruitmentApplication a = RecruitmentApplication.apply(10L, 20L, "m");
        a.accept();

        assertThatThrownBy(a::reject)
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(((BusinessException) t).getErrorCode())
                        .isEqualTo(ErrorCode.RECRUITMENT_INVALID_APPLICATION_STATE));
    }
}
