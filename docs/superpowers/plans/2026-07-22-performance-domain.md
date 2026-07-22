# PERFORMANCE 도메인 BE 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증 연주자·어드민이 연주회를 등록하고 회원이 목록(다가오는/지난/전체)·상세를 조회하는 PERFORMANCE 도메인 BE를 TDD로 구현한다.

**Architecture:** `com.back.domain.performance` 패키지에 엔티티 `Performance`(soft delete)·리포지토리(scope별 Pageable 조회)·`PerformanceService`(등록 자격 게이팅·CRUD·포스터)·`PerformanceController`를 둔다. 주최자 표시(닉네임·인증뱃지)는 도메인 경계상 `organizerId`(Long)만 저장하고 FEED에서 만든 `MemberQueryService` 배치 조회를 재사용해 파생한다. 등록 자격은 `VerifiedPerformerService.isVerified` 또는 어드민 여부로 판정한다.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / Spring Data JPA / JUnit5 + AssertJ + MockMvc / H2(test).

## Global Constraints

- Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21. 래퍼·부트 버전 올리지 말 것.
- 모든 엔티티는 `com.back.global.common.BaseEntity` 상속(createdAt/updatedAt). `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static 팩토리, 단순 접근자는 Lombok `@Getter`, setter 금지.
- 도메인 간 참조는 원시 id(Long)만. 다른 도메인 엔티티 직접 참조 금지(ARCHITECTURE-CONSTITUTION §4).
- 응답은 `com.back.global.common.ApiResponse<T>`로 감싼다(`success(data)` / `success()`). 요청/응답은 DTO(record)만. Entity 노출 금지.
- 비즈니스 예외는 `BusinessException(ErrorCode)`. 전역 `GlobalExceptionHandler`가 응답 변환. 새 에러코드는 전역 `ErrorCode` enum에 추가.
- `@SpringBootTest`에는 반드시 `@ActiveProfiles("test")`(H2). 서비스/리포지토리 단위는 `@DataJpaTest`.
- 인증 필요 API의 principal은 `@AuthenticationPrincipal Long memberId`. 어드민 판정은 `Authentication` authority 에 `"ROLE_ADMIN"` 포함 여부.
- 목록은 Spring `Pageable`(offset, 페이지 정보 포함 — DOMAIN-COMMON-STATUTE §5). 기본 size=20, 최대 50.
- 포스터 이미지는 `com.back.global.storage.FileService`로만 저장(`image/*`만 허용). 엔티티는 `posterImageKey`만 보관, URL은 `FileService.getUrl(key)`로 생성.
- 등록 자격: `VerifiedPerformerService.isVerified(organizerId)` 이거나 요청자가 ADMIN. 아니면 `NOT_VERIFIED_PERFORMER`(403-02).
- 커밋 메시지는 한글 `유형: 요약`. 커밋은 각 태스크 끝에서.

---

## 파일 구조

**신규 — `BE/src/main/java/com/back/domain/performance/`**
- `entity/Performance.java` — 공연 엔티티(soft delete)
- `repository/PerformanceRepository.java` — scope별 목록·단건(active) 조회
- `dto/PerformanceRequest.java` — 등록/수정 공용 요청(전체 교체)
- `dto/PerformanceResponse.java` — 응답(주최자 표시 포함)
- `dto/PerformanceScope.java` — 목록 범위 enum(UPCOMING/PAST/ALL)
- `service/PerformanceService.java` — 등록 게이팅/조회/목록/수정/삭제/포스터
- `controller/PerformanceController.java` — REST API

**수정**
- `global/exception/ErrorCode.java` — `PERFORMANCE_NOT_FOUND`(404-07), `NOT_VERIFIED_PERFORMER`(403-02) 추가

**재사용(기존, 변경 없음)**
- `member.service.MemberQueryService.findDisplaysByIds(Set<Long>) → Map<Long, MemberDisplay>`, `member.dto.MemberDisplay`
- `verifiedperformer.service.VerifiedPerformerService.isVerified(Long)`
- `global.storage.FileService`(`upload`/`getUrl`/`delete`), `global.storage.StoredFile`(`storageKey()`/`url()`)

---

## Task 1: Performance 엔티티

**Files:**
- Create: `BE/src/main/java/com/back/domain/performance/entity/Performance.java`
- Test: `BE/src/test/java/com/back/domain/performance/entity/PerformanceTest.java`

**Interfaces:**
- Produces: `Performance.create(Long organizerId, String title, String description, LocalDateTime performedAt, String venue, String program, String ticketInfo, String ticketUrl) -> Performance`; `performance.edit(title, description, performedAt, venue, program, ticketInfo, ticketUrl)`; `performance.changePoster(String newKey)`; `performance.delete()`; `performance.isDeleted() -> boolean`; getters `getId/getOrganizerId/getTitle/getDescription/getPerformedAt/getVenue/getProgram/getTicketInfo/getTicketUrl/getPosterImageKey/getDeletedAt`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.performance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PerformanceTest {

    private Performance sample() {
        return Performance.create(1L, "봄 리사이틀", "소개",
                LocalDateTime.of(2026, 9, 1, 19, 30), "예술의전당", "베토벤 월광", "전석 3만원",
                "https://ticket.example/1");
    }

    @Test
    void create는_미삭제_공연을_만든다() {
        Performance performance = sample();

        assertThat(performance.getOrganizerId()).isEqualTo(1L);
        assertThat(performance.getTitle()).isEqualTo("봄 리사이틀");
        assertThat(performance.getVenue()).isEqualTo("예술의전당");
        assertThat(performance.getPerformedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 19, 30));
        assertThat(performance.getPosterImageKey()).isNull();
        assertThat(performance.isDeleted()).isFalse();
    }

    @Test
    void edit는_본문_필드를_전체_교체한다() {
        Performance performance = sample();

        performance.edit("가을 리사이틀", "새 소개", LocalDateTime.of(2026, 11, 1, 20, 0),
                "롯데콘서트홀", "쇼팽 발라드", "무료", null);

        assertThat(performance.getTitle()).isEqualTo("가을 리사이틀");
        assertThat(performance.getVenue()).isEqualTo("롯데콘서트홀");
        assertThat(performance.getTicketInfo()).isEqualTo("무료");
        assertThat(performance.getTicketUrl()).isNull();
    }

    @Test
    void changePoster는_key를_교체한다() {
        Performance performance = sample();

        performance.changePoster("performance/2026/09/01/a.png");

        assertThat(performance.getPosterImageKey()).isEqualTo("performance/2026/09/01/a.png");
    }

    @Test
    void delete는_deletedAt을_마킹한다() {
        Performance performance = sample();

        performance.delete();

        assertThat(performance.isDeleted()).isTrue();
        assertThat(performance.getDeletedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.entity.PerformanceTest"`
Expected: FAIL — `Performance` 심볼 없음(컴파일 에러).

- [ ] **Step 3: Write minimal implementation**

```java
package com.back.domain.performance.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 연주회/공연. 주최자는 원시 organizerId(Long)로만 참조한다. 삭제는 soft delete. */
@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime performedAt;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(length = 2000)
    private String program;

    @Column(length = 200)
    private String ticketInfo;

    @Column(length = 500)
    private String ticketUrl;

    @Column(name = "poster_image_key")
    private String posterImageKey;

    private LocalDateTime deletedAt;

    private Performance(Long organizerId, String title, String description, LocalDateTime performedAt,
            String venue, String program, String ticketInfo, String ticketUrl) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.performedAt = performedAt;
        this.venue = venue;
        this.program = program;
        this.ticketInfo = ticketInfo;
        this.ticketUrl = ticketUrl;
    }

    public static Performance create(Long organizerId, String title, String description,
            LocalDateTime performedAt, String venue, String program, String ticketInfo,
            String ticketUrl) {
        return new Performance(organizerId, title, description, performedAt, venue, program,
                ticketInfo, ticketUrl);
    }

    /** 본문 필드를 전체 교체한다(PUT 시맨틱). 주최자·포스터·삭제상태는 바꾸지 않는다. */
    public void edit(String title, String description, LocalDateTime performedAt, String venue,
            String program, String ticketInfo, String ticketUrl) {
        this.title = title;
        this.description = description;
        this.performedAt = performedAt;
        this.venue = venue;
        this.program = program;
        this.ticketInfo = ticketInfo;
        this.ticketUrl = ticketUrl;
    }

    public void changePoster(String newKey) {
        this.posterImageKey = newKey;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.entity.PerformanceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/performance/entity/Performance.java BE/src/test/java/com/back/domain/performance/entity/PerformanceTest.java
git commit -m "feat: PERFORMANCE Performance 엔티티(soft delete) 추가"
```

---

## Task 2: PerformanceRepository (scope별 목록 + 단건)

**Files:**
- Create: `BE/src/main/java/com/back/domain/performance/repository/PerformanceRepository.java`
- Test: `BE/src/test/java/com/back/domain/performance/repository/PerformanceRepositoryTest.java`

**Interfaces:**
- Consumes: `Performance` (Task 1).
- Produces:
  - `Optional<Performance> findByIdAndDeletedAtIsNull(Long id)`
  - `Page<Performance> findByDeletedAtIsNullAndPerformedAtGreaterThanEqualOrderByPerformedAtAsc(LocalDateTime now, Pageable pageable)` — upcoming
  - `Page<Performance> findByDeletedAtIsNullAndPerformedAtLessThanOrderByPerformedAtDesc(LocalDateTime now, Pageable pageable)` — past
  - `Page<Performance> findByDeletedAtIsNullOrderByPerformedAtDesc(Pageable pageable)` — all

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.repository.PerformanceRepositoryTest"`
Expected: FAIL — `PerformanceRepository` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

```java
package com.back.domain.performance.repository;

import com.back.domain.performance.entity.Performance;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    Optional<Performance> findByIdAndDeletedAtIsNull(Long id);

    /** 다가오는 공연: 지금 이후, 가까운 순. */
    Page<Performance> findByDeletedAtIsNullAndPerformedAtGreaterThanEqualOrderByPerformedAtAsc(
            LocalDateTime now, Pageable pageable);

    /** 지난 공연: 지금 이전, 최근 순. */
    Page<Performance> findByDeletedAtIsNullAndPerformedAtLessThanOrderByPerformedAtDesc(
            LocalDateTime now, Pageable pageable);

    /** 전체(미삭제): 공연일 최신 순. */
    Page<Performance> findByDeletedAtIsNullOrderByPerformedAtDesc(Pageable pageable);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.repository.PerformanceRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/performance/repository/PerformanceRepository.java BE/src/test/java/com/back/domain/performance/repository/PerformanceRepositoryTest.java
git commit -m "feat: PERFORMANCE 리포지토리(scope별 목록/단건) 추가"
```

---

## Task 3: ErrorCode + DTO + PerformanceService (등록/조회/목록/수정/삭제)

**Files:**
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java`
- Create: `BE/src/main/java/com/back/domain/performance/dto/PerformanceRequest.java`
- Create: `BE/src/main/java/com/back/domain/performance/dto/PerformanceResponse.java`
- Create: `BE/src/main/java/com/back/domain/performance/dto/PerformanceScope.java`
- Create: `BE/src/main/java/com/back/domain/performance/service/PerformanceService.java`
- Test: `BE/src/test/java/com/back/domain/performance/service/PerformanceServiceTest.java`

**Interfaces:**
- Consumes: `PerformanceRepository` (Task 2), `MemberQueryService.findDisplaysByIds(Set<Long>) -> Map<Long, MemberDisplay>`, `VerifiedPerformerService.isVerified(Long)`, `FileService`, `ErrorCode.PERFORMANCE_NOT_FOUND/NOT_VERIFIED_PERFORMER/FORBIDDEN`.
- Produces:
  - `PerformanceRequest(String title, String description, LocalDateTime performedAt, String venue, String program, String ticketInfo, String ticketUrl)` (validated)
  - `PerformanceScope { UPCOMING, PAST, ALL }`
  - `PerformanceResponse(Long id, MemberDisplay organizer, String title, String description, LocalDateTime performedAt, String venue, String program, String ticketInfo, String ticketUrl, String posterImageUrl, LocalDateTime createdAt, LocalDateTime updatedAt)`
  - `PerformanceService`: `register(Long organizerId, boolean isAdmin, PerformanceRequest) -> PerformanceResponse`; `getPerformance(Long id) -> PerformanceResponse`; `getPerformances(PerformanceScope scope, Pageable) -> Page<PerformanceResponse>`; `editPerformance(Long editorId, Long id, PerformanceRequest) -> PerformanceResponse`; `deletePerformance(Long requesterId, boolean requesterIsAdmin, Long id) -> void`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.performance.dto.PerformanceRequest;
import com.back.domain.performance.dto.PerformanceResponse;
import com.back.domain.performance.dto.PerformanceScope;
import com.back.domain.performance.repository.PerformanceRepository;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileMetadataRepository;
import com.back.global.storage.FileService;
import com.back.global.storage.FileStorage;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class PerformanceServiceTest {

    @Autowired PerformanceRepository performanceRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;
    @Autowired FileMetadataRepository fileMetadataRepository;

    private VerifiedPerformerService verifiedPerformerService;
    private PerformanceService service;
    private Long verifiedId;
    private Long normalId;

    @BeforeEach
    void setUp() {
        verifiedPerformerService = new VerifiedPerformerService(verificationApplicationRepository);
        MemberQueryService memberQueryService =
                new MemberQueryService(memberRepository, verifiedPerformerService);
        FileService fileService = new FileService(new FakeFileStorage(), fileMetadataRepository);
        service = new PerformanceService(performanceRepository, memberQueryService,
                verifiedPerformerService, fileService);

        Member verified = memberRepository.save(
                Member.createLocal("perf", "pw", "perf@x.com", "연주자"));
        verifiedId = verified.getId();
        verifiedPerformerService.grant(new GrantRequest(verifiedId, "지정"), 99L); // 인증 처리
        Member normal = memberRepository.save(Member.createLocal("norm", "pw", "norm@x.com", "일반"));
        normalId = normal.getId();
    }

    private PerformanceRequest request(LocalDateTime when) {
        return new PerformanceRequest("리사이틀", "소개", when, "예술의전당", "베토벤", "3만원",
                "https://t.example/1");
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 인증연주자는_등록할_수_있고_주최자정보가_실린다() {
        PerformanceResponse response = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThat(response.title()).isEqualTo("리사이틀");
        assertThat(response.organizer().nickname()).isEqualTo("연주자");
        assertThat(response.organizer().verified()).isTrue();
        assertThat(response.posterImageUrl()).isNull();
    }

    @Test
    void 인증연주자가_아니면_등록은_NOT_VERIFIED_PERFORMER() {
        assertThatThrownBy(() -> service.register(normalId, false,
                request(LocalDateTime.now().plusDays(3))))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.NOT_VERIFIED_PERFORMER));
    }

    @Test
    void 어드민은_인증연주자가_아니어도_등록할_수_있다() {
        PerformanceResponse response = service.register(normalId, true,
                request(LocalDateTime.now().plusDays(3)));

        assertThat(response.title()).isEqualTo("리사이틀");
    }

    @Test
    void 단건_조회는_삭제되면_PERFORMANCE_NOT_FOUND() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));
        service.deletePerformance(verifiedId, false, created.id());

        assertThatThrownBy(() -> service.getPerformance(created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND));
    }

    @Test
    void 목록은_scope로_구분되고_주최자정보를_배치로_채운다() {
        LocalDateTime now = LocalDateTime.now();
        service.register(verifiedId, false, request(now.plusDays(1)));
        service.register(verifiedId, false, request(now.minusDays(1)));

        assertThat(service.getPerformances(PerformanceScope.UPCOMING, PageRequest.of(0, 10))
                .getContent()).hasSize(1);
        assertThat(service.getPerformances(PerformanceScope.PAST, PageRequest.of(0, 10))
                .getContent()).hasSize(1);
        assertThat(service.getPerformances(PerformanceScope.ALL, PageRequest.of(0, 10))
                .getContent()).hasSize(2);
        assertThat(service.getPerformances(PerformanceScope.ALL, PageRequest.of(0, 10))
                .getContent().get(0).organizer().nickname()).isEqualTo("연주자");
    }

    @Test
    void 주최자가_아니면_수정은_FORBIDDEN() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.editPerformance(normalId, created.id(),
                request(LocalDateTime.now().plusDays(5))))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 주최자는_수정할_수_있다() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        PerformanceResponse updated = service.editPerformance(verifiedId, created.id(),
                new PerformanceRequest("바뀐공연", "소개2", LocalDateTime.now().plusDays(5), "롯데홀",
                        null, null, null));

        assertThat(updated.title()).isEqualTo("바뀐공연");
        assertThat(updated.venue()).isEqualTo("롯데홀");
    }

    @Test
    void 타인_삭제는_FORBIDDEN이지만_어드민은_가능하다() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.deletePerformance(normalId, false, created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deletePerformance(normalId, true, created.id()); // 어드민 모더레이션
        assertThat(performanceRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
    }

    /** 디스크/AWS 없이 검증하기 위한 인메모리 저장소(FileServiceTest 패턴). */
    static class FakeFileStorage implements FileStorage {
        final Map<String, byte[]> stored = new HashMap<>();

        @Override
        public String upload(String key, InputStream content, long size, String contentType) {
            try {
                stored.put(key, content.readAllBytes());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return key;
        }

        @Override
        public void delete(String key) {
            stored.remove(key);
        }

        @Override
        public String getUrl(String key) {
            return "http://fake/" + key;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.service.PerformanceServiceTest"`
Expected: FAIL — `ErrorCode.NOT_VERIFIED_PERFORMER`/DTO/`PerformanceService` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`ErrorCode.java` — MEMBER 블록의 `MEMBER_NOT_FOUND` 아래(또는 FEED 블록 근처)에 아래를 추가한다. `403-01 FORBIDDEN` 은 이미 존재하므로 새 상수만 추가하며, enum 마지막 상수의 세미콜론 위치를 지킨다(마지막 상수 뒤 `;` 앞에 삽입).
```java
    // --- PERFORMANCE ---
    PERFORMANCE_NOT_FOUND("404-07", HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
    NOT_VERIFIED_PERFORMER("403-02", HttpStatus.FORBIDDEN, "인증 연주자만 공연을 등록할 수 있습니다."),
```

`PerformanceScope.java`:
```java
package com.back.domain.performance.dto;

/** 공연 목록 조회 범위. */
public enum PerformanceScope {
    UPCOMING,
    PAST,
    ALL
}
```

`PerformanceRequest.java`:
```java
package com.back.domain.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 공연 등록/수정 공용 요청(전체 교체). */
public record PerformanceRequest(
        @NotBlank(message = "공연명을 입력해 주세요.")
        @Size(max = 100, message = "공연명은 100자를 넘을 수 없습니다.") String title,
        @Size(max = 2000, message = "소개는 2000자를 넘을 수 없습니다.") String description,
        @NotNull(message = "공연 일시를 입력해 주세요.") LocalDateTime performedAt,
        @NotBlank(message = "장소를 입력해 주세요.")
        @Size(max = 200, message = "장소는 200자를 넘을 수 없습니다.") String venue,
        @Size(max = 2000, message = "프로그램은 2000자를 넘을 수 없습니다.") String program,
        @Size(max = 200, message = "관람료 안내는 200자를 넘을 수 없습니다.") String ticketInfo,
        @Size(max = 500, message = "링크는 500자를 넘을 수 없습니다.") String ticketUrl) {
}
```

`PerformanceResponse.java`:
```java
package com.back.domain.performance.dto;

import com.back.domain.member.dto.MemberDisplay;
import java.time.LocalDateTime;

public record PerformanceResponse(
        Long id,
        MemberDisplay organizer,
        String title,
        String description,
        LocalDateTime performedAt,
        String venue,
        String program,
        String ticketInfo,
        String ticketUrl,
        String posterImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
```

`PerformanceService.java`:
```java
package com.back.domain.performance.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.performance.dto.PerformanceRequest;
import com.back.domain.performance.dto.PerformanceResponse;
import com.back.domain.performance.dto.PerformanceScope;
import com.back.domain.performance.entity.Performance;
import com.back.domain.performance.repository.PerformanceRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연주회 등록/조회/목록/수정/삭제. 등록 자격은 인증 연주자 또는 어드민.
 * 주최자 표시정보는 MEMBER 협력(배치)으로 파생한다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final MemberQueryService memberQueryService;
    private final VerifiedPerformerService verifiedPerformerService;
    private final FileService fileService;

    @Transactional
    public PerformanceResponse register(Long organizerId, boolean isAdmin, PerformanceRequest request) {
        if (!isAdmin && !verifiedPerformerService.isVerified(organizerId)) {
            throw new BusinessException(ErrorCode.NOT_VERIFIED_PERFORMER);
        }
        Performance saved = performanceRepository.save(Performance.create(organizerId,
                request.title(), request.description(), request.performedAt(), request.venue(),
                request.program(), request.ticketInfo(), request.ticketUrl()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PerformanceResponse getPerformance(Long id) {
        return toResponse(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<PerformanceResponse> getPerformances(PerformanceScope scope, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<Performance> page = switch (scope) {
            case UPCOMING -> performanceRepository
                    .findByDeletedAtIsNullAndPerformedAtGreaterThanEqualOrderByPerformedAtAsc(now, pageable);
            case PAST -> performanceRepository
                    .findByDeletedAtIsNullAndPerformedAtLessThanOrderByPerformedAtDesc(now, pageable);
            case ALL -> performanceRepository.findByDeletedAtIsNullOrderByPerformedAtDesc(pageable);
        };
        Set<Long> organizerIds = page.getContent().stream()
                .map(Performance::getOrganizerId).collect(Collectors.toSet());
        Map<Long, MemberDisplay> organizers = memberQueryService.findDisplaysByIds(organizerIds);
        return page.map(p -> toResponse(p, organizers.get(p.getOrganizerId())));
    }

    @Transactional
    public PerformanceResponse editPerformance(Long editorId, Long id, PerformanceRequest request) {
        Performance performance = findActive(id);
        if (!performance.getOrganizerId().equals(editorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        performance.edit(request.title(), request.description(), request.performedAt(),
                request.venue(), request.program(), request.ticketInfo(), request.ticketUrl());
        return toResponse(performance);
    }

    @Transactional
    public void deletePerformance(Long requesterId, boolean requesterIsAdmin, Long id) {
        Performance performance = findActive(id);
        if (!performance.getOrganizerId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        performance.delete();
    }

    private Performance findActive(Long id) {
        return performanceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }

    private PerformanceResponse toResponse(Performance performance) {
        MemberDisplay organizer = memberQueryService
                .findDisplaysByIds(Set.of(performance.getOrganizerId()))
                .get(performance.getOrganizerId());
        return toResponse(performance, organizer);
    }

    private PerformanceResponse toResponse(Performance p, MemberDisplay organizer) {
        String posterUrl = p.getPosterImageKey() == null
                ? null
                : fileService.getUrl(p.getPosterImageKey());
        return new PerformanceResponse(p.getId(), organizer, p.getTitle(), p.getDescription(),
                p.getPerformedAt(), p.getVenue(), p.getProgram(), p.getTicketInfo(),
                p.getTicketUrl(), posterUrl, p.getCreatedAt(), p.getUpdatedAt());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.service.PerformanceServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/global/exception/ErrorCode.java BE/src/main/java/com/back/domain/performance/dto/ BE/src/main/java/com/back/domain/performance/service/PerformanceService.java BE/src/test/java/com/back/domain/performance/service/PerformanceServiceTest.java
git commit -m "feat: PERFORMANCE 서비스(등록 자격·조회/목록/수정/삭제) + 에러코드 404-07/403-02"
```

---

## Task 4: PerformanceService.updatePoster (포스터 이미지)

**Files:**
- Modify: `BE/src/main/java/com/back/domain/performance/service/PerformanceService.java`
- Test: `BE/src/test/java/com/back/domain/performance/service/PerformanceServiceTest.java` (기존 파일에 테스트 추가)

**Interfaces:**
- Consumes: `FileService.upload(MultipartFile, String directory, Long uploaderId) -> StoredFile` (`storageKey()`/`url()`), `FileService.getUrl(String)`, `FileService.delete(String)`, `ErrorCode.INVALID_FILE`.
- Produces: `PerformanceService.updatePoster(Long organizerId, Long id, MultipartFile file) -> PerformanceResponse` (주최자만, `image/*`만, 교체 시 옛 파일 삭제).

- [ ] **Step 1: Write the failing test** (기존 `PerformanceServiceTest`에 추가; 클래스 상단에 필요한 import 추가: `org.springframework.mock.web.MockMultipartFile`, `java.nio.charset.StandardCharsets`)

```java
    private MockMultipartFile png() {
        return new MockMultipartFile("file", "poster.png", "image/png",
                "img".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 주최자는_포스터를_올릴_수_있고_url이_실린다() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        PerformanceResponse withPoster = service.updatePoster(verifiedId, created.id(), png());

        assertThat(withPoster.posterImageUrl()).isNotBlank();
        assertThat(performanceRepository.findByIdAndDeletedAtIsNull(created.id())
                .orElseThrow().getPosterImageKey()).startsWith("performance/").endsWith(".png");
    }

    @Test
    void 주최자가_아니면_포스터_업로드는_FORBIDDEN() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.updatePoster(normalId, created.id(), png()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 이미지가_아닌_포스터는_INVALID_FILE() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf",
                "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.updatePoster(verifiedId, created.id(), pdf))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.INVALID_FILE));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.service.PerformanceServiceTest"`
Expected: FAIL — `updatePoster` 심볼 없음.

- [ ] **Step 3: Write minimal implementation** (`PerformanceService`에 추가; import 추가: `org.springframework.web.multipart.MultipartFile`, `com.back.global.storage.StoredFile`)

클래스 상단 상수 추가:
```java
    private static final String POSTER_DIRECTORY = "performance";
```

메서드 추가:
```java
    @Transactional
    public PerformanceResponse updatePoster(Long organizerId, Long id, MultipartFile file) {
        validateImage(file);
        Performance performance = findActive(id);
        if (!performance.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String oldKey = performance.getPosterImageKey();
        StoredFile stored = fileService.upload(file, POSTER_DIRECTORY, organizerId);
        performance.changePoster(stored.storageKey());
        // 새 이미지 저장이 확정된 뒤에만 옛 파일을 제거한다 — 실패해도 이미지 유실 없음(프로필 이미지 패턴).
        if (oldKey != null) {
            fileService.delete(oldKey);
        }
        return toResponse(performance);
    }

    /** 이미지 타입만 허용. 크기 상한은 전역 multipart 설정이 담당. */
    private void validateImage(MultipartFile file) {
        if (file == null || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "이미지 파일만 업로드할 수 있습니다.");
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.service.PerformanceServiceTest"`
Expected: PASS(기존 + 신규 포함).

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/performance/service/PerformanceService.java BE/src/test/java/com/back/domain/performance/service/PerformanceServiceTest.java
git commit -m "feat: PERFORMANCE 포스터 이미지 업로드(주최자만, image/*) 추가"
```

---

## Task 5: PerformanceController

**Files:**
- Create: `BE/src/main/java/com/back/domain/performance/controller/PerformanceController.java`
- Test: `BE/src/test/java/com/back/domain/performance/controller/PerformanceControllerTest.java`

**Interfaces:**
- Consumes: `PerformanceService` (Tasks 3–4), `ApiResponse`, `@AuthenticationPrincipal Long`, `Authentication`.
- 어드민 판정: 컨트롤러가 `Authentication` authority 에서 `ROLE_ADMIN` 여부를 계산(package-private static `isAdmin`).

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.performance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class PerformanceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired VerifiedPerformerService verifiedPerformerService;

    private String verifiedBearer;
    private String normalBearer;
    private String adminBearer;

    private static final String BODY = "{\"title\":\"리사이틀\",\"description\":\"소개\","
            + "\"performedAt\":\"2026-12-01T19:30:00\",\"venue\":\"예술의전당\","
            + "\"program\":\"베토벤\",\"ticketInfo\":\"3만원\",\"ticketUrl\":\"https://t.example/1\"}";

    @BeforeEach
    void setUp() {
        Member verified = memberRepository.save(Member.createLocal("perf", "pw", "perf@x.com", "연주자"));
        verifiedPerformerService.grant(new GrantRequest(verified.getId(), "지정"), 99L);
        verifiedBearer = "Bearer " + jwtProvider.createAccessToken(verified.getId(), Role.USER);

        Member normal = memberRepository.save(Member.createLocal("norm", "pw", "norm@x.com", "일반"));
        normalBearer = "Bearer " + jwtProvider.createAccessToken(normal.getId(), Role.USER);

        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
    }

    private String register(String bearer) throws Exception {
        String json = mockMvc.perform(post("/api/performances").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 토큰_없이_등록은_401() throws Exception {
        mockMvc.perform(post("/api/performances")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증연주자는_등록하고_목록에_보인다() throws Exception {
        register(verifiedBearer);
        mockMvc.perform(get("/api/performances").param("scope", "UPCOMING")
                        .header("Authorization", verifiedBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("리사이틀"))
                .andExpect(jsonPath("$.data.content[0].organizer.nickname").value("연주자"));
    }

    @Test
    void 일반회원_등록은_403_NOT_VERIFIED_PERFORMER() throws Exception {
        mockMvc.perform(post("/api/performances").header("Authorization", normalBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-02"));
    }

    @Test
    void 어드민은_등록할_수_있다() throws Exception {
        mockMvc.perform(post("/api/performances").header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("리사이틀"));
    }

    @Test
    void 공연명_누락은_400() throws Exception {
        mockMvc.perform(post("/api/performances").header("Authorization", verifiedBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"performedAt\":\"2026-12-01T19:30:00\",\"venue\":\"홀\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 타인_수정은_403() throws Exception {
        String id = register(verifiedBearer);
        mockMvc.perform(put("/api/performances/" + id).header("Authorization", normalBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 어드민은_타인_공연을_삭제할_수_있다() throws Exception {
        String id = register(verifiedBearer);
        mockMvc.perform(delete("/api/performances/" + id).header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/performances/" + id).header("Authorization", verifiedBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-07"));
    }

    @Test
    void 주최자는_포스터를_올린다() throws Exception {
        String id = register(verifiedBearer);
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png",
                "img".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/performances/" + id + "/poster")
                        .file(file).header("Authorization", verifiedBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posterImageUrl").isNotEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.controller.PerformanceControllerTest"`
Expected: FAIL — 컨트롤러 없음(404/401 등 불일치).

- [ ] **Step 3: Write minimal implementation**

```java
package com.back.domain.performance.controller;

import com.back.domain.performance.dto.PerformanceRequest;
import com.back.domain.performance.dto.PerformanceResponse;
import com.back.domain.performance.dto.PerformanceScope;
import com.back.domain.performance.service.PerformanceService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 연주회 API. 모두 인증 필요(principal = memberId).
 * 등록은 인증 연주자 또는 어드민(서비스에서 판정), 모더레이션 삭제는 역할로 판정 — 어드민 전용 경로 없음.
 */
@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PerformanceService performanceService;

    @PostMapping
    public ApiResponse<PerformanceResponse> register(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @Valid @RequestBody PerformanceRequest request) {
        return ApiResponse.success(
                performanceService.register(memberId, isAdmin(authentication), request));
    }

    @GetMapping
    public ApiResponse<Page<PerformanceResponse>> list(
            @RequestParam(defaultValue = "UPCOMING") PerformanceScope scope,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(performanceService.getPerformances(scope,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<PerformanceResponse> get(@PathVariable Long id) {
        return ApiResponse.success(performanceService.getPerformance(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PerformanceResponse> edit(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id, @Valid @RequestBody PerformanceRequest request) {
        return ApiResponse.success(performanceService.editPerformance(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        performanceService.deletePerformance(memberId, isAdmin(authentication), id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/poster")
    public ApiResponse<PerformanceResponse> poster(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(performanceService.updatePoster(memberId, id, file));
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.performance.controller.PerformanceControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/performance/controller/PerformanceController.java BE/src/test/java/com/back/domain/performance/controller/PerformanceControllerTest.java
git commit -m "feat: PERFORMANCE 컨트롤러(등록/목록/상세/수정/삭제/포스터) 추가"
```

---

## Task 6: 전체 회귀 + 문서 반영

**Files:**
- Modify: `docs/CONTEXT.md`, `docs/TODO-BACKLOG.md`, `docs/TODO-DONE.md`, `docs/AI-ACTION-LOGS.md`, `docs/DOMAIN-PERFORMANCE-STATUTE.md`(§11 확정 반영)

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew -p BE test`
Expected: BUILD SUCCESSFUL. 실패 시 해당 태스크로 돌아가 수정.

- [ ] **Step 2: 문서 반영**
- CONTEXT: 구현된 도메인에 PERFORMANCE 추가(2026-07-22 BE), API/에러코드 요약 1줄.
- TODO-BACKLOG: PERFORMANCE 항목 완료 처리.
- TODO-DONE: 2026-07-22 PERFORMANCE BE 완료 기록.
- AI-ACTION-LOGS: 구현 로그(파일 끝에 추가).
- DOMAIN-PERFORMANCE-STATUTE §11: 확정값 반영(포스터 응답=갱신된 PerformanceResponse, size 기본 20/최대 50, 과거 일시 등록 허용, edit 검증=등록과 동일 @Valid, 정규화는 별도 트림 없이 @NotBlank).

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: PERFORMANCE BE 구현 반영(상태/TODO/로그/STATUTE 확정)"
```

---

## 확정한 세부값 (STATUTE §11 대응)

- 포스터 업로드 응답 = 갱신된 `PerformanceResponse`(이미지 URL 포함).
- 목록 기본 size=20, 최대 50(초과 clamp, `<1`이면 기본). page `<0`이면 0.
- `performedAt` 과거 일시 등록 허용(과거 공연 기록용). scope=past 로 노출.
- `edit`은 등록과 동일 `PerformanceRequest`(`@Valid`, 전체 교체).
- `description`/`program` 정규화는 별도 트림 없이 `@NotBlank`(title/venue) + `@Size`로만 제약.
- 어드민 판정 `isAdmin`은 `PerformanceController` 로컬 static(향후 FEED의 것과 공용 헬퍼로 추출 여지 — BACKLOG).
