package com.back.domain.verifiedperformer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.verifiedperformer.entity.VerificationApplication;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class VerificationApplicationRepositoryTest {

    @Autowired
    private VerificationApplicationRepository repository;

    @Test
    void 활성_신청_존재_여부를_상태로_판정한다() {
        repository.save(VerificationApplication.apply(1L, "사유", List.of()));

        assertThat(repository.existsByMemberIdAndStatus(1L, VerificationStatus.PENDING)).isTrue();
        assertThat(repository.existsByMemberIdAndStatus(1L, VerificationStatus.APPROVED)).isFalse();
        assertThat(repository.existsByMemberIdAndStatus(2L, VerificationStatus.PENDING)).isFalse();
    }

    @Test
    void 내_최신_신청을_생성순_역순으로_가져온다() {
        VerificationApplication first = repository.save(VerificationApplication.apply(1L, "첫 신청", List.of()));
        first.reject(99L, "거절");
        repository.saveAndFlush(first);
        VerificationApplication second = repository.saveAndFlush(
                VerificationApplication.apply(1L, "재신청", List.of()));

        VerificationApplication latest =
                repository.findTopByMemberIdOrderByCreatedAtDescIdDesc(1L).orElseThrow();

        assertThat(latest.getId()).isEqualTo(second.getId());
        assertThat(latest.getStatement()).isEqualTo("재신청");
    }

    @Test
    void 이력이_없으면_최신_신청은_비어있다() {
        assertThat(repository.findTopByMemberIdOrderByCreatedAtDescIdDesc(404L)).isEmpty();
    }

    @Test
    void 상태별_목록을_페이징으로_가져온다() {
        repository.save(VerificationApplication.apply(1L, "대기1", List.of()));
        repository.save(VerificationApplication.apply(2L, "대기2", List.of()));
        VerificationApplication approved = VerificationApplication.apply(3L, "승인건", List.of());
        approved.approve(99L, "승인");
        repository.save(approved);

        List<VerificationApplication> pending = repository
                .findByStatusOrderByCreatedAtDescIdDesc(VerificationStatus.PENDING, PageRequest.of(0, 10))
                .getContent();

        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(a -> a.getStatus() == VerificationStatus.PENDING);
    }
}
