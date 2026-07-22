package com.back.domain.recruitment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class RecruitmentRepositoryTest {

    @Autowired RecruitmentPostingRepository postingRepository;
    @Autowired RecruitmentApplicationRepository applicationRepository;

    private RecruitmentPosting save(Set<Instrument> instruments, LocalDateTime deadline,
            boolean closed) {
        RecruitmentPosting p = RecruitmentPosting.create(1L, "구인", "설명", instruments, 1,
                "서울", "무보수", deadline);
        if (closed) {
            p.close();
        }
        return postingRepository.save(p);
    }

    @Test
    void findOpen은_모집중이면서_악기_필터를_적용한다() {
        save(Set.of(Instrument.CELLO), LocalDateTime.now().plusDays(3), false); // open, cello
        save(Set.of(Instrument.VIOLIN), LocalDateTime.now().plusDays(3), false); // open, violin
        save(Set.of(Instrument.CELLO), LocalDateTime.now().minusDays(1), false); // deadline 지남 → 제외
        save(Set.of(Instrument.CELLO), LocalDateTime.now().plusDays(3), true); // CLOSED → 제외

        var all = postingRepository.findOpen(RecruitmentStatus.OPEN, LocalDateTime.now(), null,
                PageRequest.of(0, 10));
        var cello = postingRepository.findOpen(RecruitmentStatus.OPEN, LocalDateTime.now(),
                Instrument.CELLO, PageRequest.of(0, 10));

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(cello.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findClosed는_수동마감_또는_마감일지남을_포함한다() {
        save(Set.of(Instrument.CELLO), LocalDateTime.now().plusDays(3), false); // open → 제외
        save(Set.of(Instrument.CELLO), LocalDateTime.now().minusDays(1), false); // 마감일 지남
        save(Set.of(Instrument.CELLO), LocalDateTime.now().plusDays(3), true); // CLOSED

        var closed = postingRepository.findClosed(RecruitmentStatus.CLOSED, LocalDateTime.now(),
                null, PageRequest.of(0, 10));

        assertThat(closed.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 상시모집_deadline_null은_findOpen에_포함된다() {
        save(Set.of(Instrument.PIANO), null, false);

        var open = postingRepository.findOpen(RecruitmentStatus.OPEN, LocalDateTime.now(), null,
                PageRequest.of(0, 10));

        assertThat(open.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 활성지원_존재판정과_목록조회() {
        applicationRepository.save(RecruitmentApplication.apply(100L, 200L, "m1"));
        applicationRepository.save(RecruitmentApplication.apply(100L, 201L, "m2"));

        boolean exists = applicationRepository.existsByPostingIdAndApplicantIdAndStatusIn(
                100L, 200L,
                List.of(RecruitmentApplicationStatus.PENDING, RecruitmentApplicationStatus.ACCEPTED));
        var byPosting = applicationRepository.findByPostingIdOrderByCreatedAtDescIdDesc(100L,
                PageRequest.of(0, 10));
        var byApplicant = applicationRepository.findByApplicantIdOrderByCreatedAtDescIdDesc(200L,
                PageRequest.of(0, 10));

        assertThat(exists).isTrue();
        assertThat(byPosting.getTotalElements()).isEqualTo(2);
        assertThat(byApplicant.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findAllActive는_삭제된공고를_제외하고_악기필터를_적용한다() {
        save(Set.of(Instrument.CELLO), LocalDateTime.now().plusDays(3), false); // open
        save(Set.of(Instrument.CELLO), LocalDateTime.now().plusDays(3), true); // closed
        save(Set.of(Instrument.CELLO), LocalDateTime.now().minusDays(1), false); // 마감일 지남
        RecruitmentPosting deleted = save(Set.of(Instrument.CELLO),
                LocalDateTime.now().plusDays(3), false);
        deleted.delete();
        postingRepository.save(deleted);

        var all = postingRepository.findAllActive(null, PageRequest.of(0, 10));
        var violin = postingRepository.findAllActive(Instrument.VIOLIN, PageRequest.of(0, 10));

        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(violin.getTotalElements()).isEqualTo(0);
        assertThat(all.getContent()).noneMatch(p -> p.getId().equals(deleted.getId()));
    }

    @Test
    void deadline이_now와_정확히_같으면_CLOSED로_분류된다() {
        LocalDateTime now = LocalDateTime.now();
        save(Set.of(Instrument.CELLO), now, false);

        var open = postingRepository.findOpen(RecruitmentStatus.OPEN, now, null,
                PageRequest.of(0, 10));
        var closed = postingRepository.findClosed(RecruitmentStatus.CLOSED, now, null,
                PageRequest.of(0, 10));

        assertThat(open.getTotalElements()).isEqualTo(0);
        assertThat(closed.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 철회된_지원은_활성지원_존재판정에서_제외된다() {
        RecruitmentApplication application = RecruitmentApplication.apply(300L, 400L, "m1");
        application.withdraw();
        applicationRepository.save(application);

        boolean exists = applicationRepository.existsByPostingIdAndApplicantIdAndStatusIn(
                300L, 400L,
                List.of(RecruitmentApplicationStatus.PENDING, RecruitmentApplicationStatus.ACCEPTED));

        assertThat(exists).isFalse();
    }
}
