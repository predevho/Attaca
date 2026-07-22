package com.back.domain.performance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.performance.entity.Performance;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class PerformanceRepositoryTest {

    @Autowired
    private PerformanceRepository repository;

    private Performance at(String title, LocalDateTime when) {
        return Performance.create(1L, title, null, when, "홀", null, null, null);
    }

    @Test
    void 미삭제_단건만_조회된다() {
        Performance saved = repository.save(at("A", LocalDateTime.now().plusDays(1)));
        assertThat(repository.findByIdAndDeletedAtIsNull(saved.getId())).isPresent();

        saved.delete();
        repository.saveAndFlush(saved);
        assertThat(repository.findByIdAndDeletedAtIsNull(saved.getId())).isEmpty();
    }

    @Test
    void upcoming은_다가오는_공연을_가까운순으로_준다() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(at("과거", now.minusDays(1)));
        Performance soon = repository.save(at("가까운미래", now.plusDays(1)));
        Performance later = repository.save(at("먼미래", now.plusDays(10)));

        List<Performance> upcoming = repository
                .findByDeletedAtIsNullAndPerformedAtGreaterThanEqualOrderByPerformedAtAsc(
                        now, PageRequest.of(0, 10))
                .getContent();

        assertThat(upcoming).extracting(Performance::getId)
                .containsExactly(soon.getId(), later.getId());
    }

    @Test
    void past는_지난_공연을_최근순으로_준다() {
        LocalDateTime now = LocalDateTime.now();
        Performance old = repository.save(at("오래전", now.minusDays(10)));
        Performance recent = repository.save(at("최근지남", now.minusDays(1)));
        repository.save(at("미래", now.plusDays(1)));

        List<Performance> past = repository
                .findByDeletedAtIsNullAndPerformedAtLessThanOrderByPerformedAtDesc(
                        now, PageRequest.of(0, 10))
                .getContent();

        assertThat(past).extracting(Performance::getId)
                .containsExactly(recent.getId(), old.getId());
    }

    @Test
    void 삭제된_공연은_목록에서_빠진다() {
        LocalDateTime now = LocalDateTime.now();
        Performance a = repository.save(at("살아있음", now.plusDays(1)));
        Performance b = repository.save(at("삭제됨", now.plusDays(2)));
        b.delete();
        repository.saveAndFlush(b);

        List<Performance> all = repository
                .findByDeletedAtIsNullOrderByPerformedAtDesc(PageRequest.of(0, 10)).getContent();

        assertThat(all).extracting(Performance::getId).containsExactly(a.getId());
    }
}
