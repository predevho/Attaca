# RECRUITMENT(구인) 도메인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증 회원이 연주자 구인 공고를 등록·홍보하고 다른 회원이 지원(수락/거절/철회)하는 RECRUITMENT 도메인을 BE에 추가한다.

**Architecture:** PERFORMANCE/FEED 도메인 패턴을 그대로 따른다. 두 엔티티(`RecruitmentPosting` soft delete, `RecruitmentApplication` 상태머신)를 두고, 작성자·지원자는 원시 `Long` ID로만 참조하며 표시정보(닉네임·인증뱃지)는 `MemberQueryService.findDisplaysByIds` 배치 협력으로 파생한다(N+1 없음). 컨트롤러는 `@AuthenticationPrincipal Long memberId` + `Authentication` 으로 principal/역할을 판정한다.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / Spring Data JPA / MySQL(운영) · H2(test) / JUnit5 · AssertJ · MockMvc.

## Global Constraints

- Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21 toolchain. Gradle 래퍼 올리지 말 것.
- 빌드/테스트는 `BE/` 디렉터리에서 실행: `cd BE && ./gradlew ...` (Windows PowerShell은 `cd BE; ./gradlew ...`).
- `@SpringBootTest` 계열에는 반드시 `@ActiveProfiles("test")`(H2). 없으면 MySQL 접속 시도로 실패.
- 엔티티 규칙: `BaseEntity` 상속, `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, private 생성자 + static 팩토리, setter 금지(의도가 드러나는 변경 메서드). 단순 접근자는 Lombok `@Getter`.
- 응답 래퍼는 `ApiResponse.success(...)`. 에러 본문은 `ErrorBody(resultCode, code, message)`.
- 예외는 `throw new BusinessException(ErrorCode.XXX)` (또는 `(ErrorCode.XXX, "메시지")`).
- 도메인 간 연관 매핑 금지: 작성자/지원자는 원시 `Long`. 표시정보는 서비스 협력 + IN 배치 조회로 조립.
- 검증 실패는 `@Valid` + Bean Validation → 400-01 로 자동 매핑(전역 핸들러 기존 구현).
- 커밋 메시지는 한글, `유형: 요약` 제목 + 한글 불릿 본문. 커밋/푸시는 각 태스크 끝에서 수행하되, **푸시는 하지 않는다**(사용자 요청 시에만).
- 기준 문서(spec): `docs/superpowers/specs/2026-07-22-recruitment-design.md`.

---

### Task 1: 도메인 정본 문서 + 에러 코드 6종

**Files:**
- Create: `docs/DOMAIN-RECRUITMENT-CONSTITUTION.md`
- Create: `docs/DOMAIN-RECRUITMENT-STATUTE.md`
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java` (RECRUITMENT 블록 추가)

**Interfaces:**
- Produces: `ErrorCode.RECRUITMENT_NOT_FOUND`, `ErrorCode.RECRUITMENT_APPLICATION_NOT_FOUND`, `ErrorCode.RECRUITMENT_CLOSED`, `ErrorCode.ALREADY_APPLIED`, `ErrorCode.CANNOT_APPLY_OWN_RECRUITMENT`, `ErrorCode.RECRUITMENT_INVALID_APPLICATION_STATE` — 이후 모든 태스크가 사용.

- [ ] **Step 1: 정본 문서 2개 작성**

`docs/DOMAIN-RECRUITMENT-CONSTITUTION.md` 는 spec §1~3, §10 을 정본 형식으로 정리한다. PERFORMANCE-CONSTITUTION 구조(책임 / 원칙 / 관계 / 범위 밖)를 따르되 핵심만:
- 책임: 구인 공고 CRUD·마감·조회 + 지원 생성·상태전이·조회.
- 원칙: 등록 자격 = **인증 회원 누구나**(PERFORMANCE와 달리 인증 연주자 게이팅 없음), 작성자·지원자는 원시 Long 참조, 공고 soft delete, 지원은 상태(WITHDRAWN)로 철회, 활성 지원(PENDING/ACCEPTED) 유일, 목록 페이징.
- 관계: MEMBER(표시정보 협력·ROLE_ADMIN 모더레이션), VERIFIED-PERFORMER(뱃지 파생만, 게이팅 아님), PERFORMANCE(현재 연결 없음).
- 범위 밖: spec §10 그대로.

`docs/DOMAIN-RECRUITMENT-STATUTE.md` 는 spec §4~9 를 정본 구현 규칙으로 정리한다. PERFORMANCE-STATUTE 구조(패키지 / 엔티티 / 권한 / soft delete / 표시 협력 / 페이징 / API / 에러코드 / 테스트)를 따른다. 엔티티 필드표·API 표·에러코드 표는 아래 태스크에서 확정하는 시그니처와 일치시킨다.

- [ ] **Step 2: ErrorCode 에 RECRUITMENT 블록 추가**

`ErrorCode.java` 의 PERFORMANCE 블록 뒤(마지막 enum 상수 `NOT_VERIFIED_PERFORMER` 의 `;` 를 `,` 로 바꾸고 그 아래)에 추가:

```java
    // --- RECRUITMENT(구인) ---
    RECRUITMENT_NOT_FOUND("404-08", HttpStatus.NOT_FOUND, "구인 공고를 찾을 수 없습니다."),
    RECRUITMENT_APPLICATION_NOT_FOUND("404-09", HttpStatus.NOT_FOUND, "지원 내역을 찾을 수 없습니다."),
    RECRUITMENT_CLOSED("409-07", HttpStatus.CONFLICT, "마감된 공고에는 지원할 수 없습니다."),
    ALREADY_APPLIED("409-08", HttpStatus.CONFLICT, "이미 지원한 공고입니다."),
    CANNOT_APPLY_OWN_RECRUITMENT("409-09", HttpStatus.CONFLICT, "본인이 올린 공고에는 지원할 수 없습니다."),
    RECRUITMENT_INVALID_APPLICATION_STATE("409-10", HttpStatus.CONFLICT, "이미 처리된 지원은 다시 처리할 수 없습니다.");
```

(직전 상수 `NOT_VERIFIED_PERFORMER("403-02", ...)` 끝의 `;` 를 `,` 로 변경.)

- [ ] **Step 3: 컴파일 확인**

Run: `cd BE && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL (에러 코드 6종이 컴파일됨).

- [ ] **Step 4: 커밋**

```bash
git add docs/DOMAIN-RECRUITMENT-CONSTITUTION.md docs/DOMAIN-RECRUITMENT-STATUTE.md BE/src/main/java/com/back/global/exception/ErrorCode.java
git commit -m "docs: RECRUITMENT 도메인 문서 작성 및 에러코드 추가

- 정본 문서 CONSTITUTION/STATUTE 작성(등록 자격=인증 회원 누구나, 지원 상태머신)
- 전역 ErrorCode에 RECRUITMENT 6종 추가(404-08/09, 409-07~10)"
```

---

### Task 2: 상태 enum 2종 + RecruitmentPosting 엔티티

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentStatus.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentApplicationStatus.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentPosting.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/entity/RecruitmentPostingTest.java`

**Interfaces:**
- Produces:
  - `enum RecruitmentStatus { OPEN, CLOSED }`
  - `enum RecruitmentApplicationStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN }`
  - `RecruitmentPosting.create(Long authorId, String title, String description, Set<Instrument> instruments, Integer recruitCount, String location, String fee, LocalDateTime deadline) -> RecruitmentPosting`
  - `posting.edit(String title, String description, Set<Instrument> instruments, Integer recruitCount, String location, String fee, LocalDateTime deadline)` (본문 전체 교체)
  - `posting.close()` (status→CLOSED), `posting.delete()`, `posting.isDeleted() -> boolean`, `posting.isClosed(LocalDateTime now) -> boolean`
  - getters: `getId, getAuthorId, getTitle, getDescription, getInstruments, getRecruitCount, getLocation, getFee, getDeadline, getStatus, getCreatedAt, getUpdatedAt`

- [ ] **Step 1: 상태 enum 2종 작성**

`RecruitmentStatus.java`:
```java
package com.back.domain.recruitment.entity;

/** 구인 공고 상태. 마감은 이 상태(수동) 또는 deadline 경과(파생)로 판정한다. */
public enum RecruitmentStatus {
    OPEN,
    CLOSED
}
```

`RecruitmentApplicationStatus.java`:
```java
package com.back.domain.recruitment.entity;

/** 지원 상태머신. PENDING → ACCEPTED/REJECTED(작성자) 또는 WITHDRAWN(지원자, PENDING에서만). */
public enum RecruitmentApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
```

- [ ] **Step 2: 실패하는 엔티티 테스트 작성**

`RecruitmentPostingTest.java`:
```java
package com.back.domain.recruitment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.entity.Instrument;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecruitmentPostingTest {

    private RecruitmentPosting posting(LocalDateTime deadline) {
        return RecruitmentPosting.create(1L, "첼로 구합니다", "정기연주회 단원 모집",
                Set.of(Instrument.CELLO, Instrument.VIOLA), 2, "서울 강남", "회당 5만원", deadline);
    }

    @Test
    void 생성되면_OPEN이고_필드가_담긴다() {
        RecruitmentPosting p = posting(LocalDateTime.now().plusDays(7));

        assertThat(p.getStatus()).isEqualTo(RecruitmentStatus.OPEN);
        assertThat(p.getAuthorId()).isEqualTo(1L);
        assertThat(p.getInstruments()).containsExactlyInAnyOrder(Instrument.CELLO, Instrument.VIOLA);
        assertThat(p.getRecruitCount()).isEqualTo(2);
        assertThat(p.isDeleted()).isFalse();
    }

    @Test
    void edit는_본문을_전체교체한다() {
        RecruitmentPosting p = posting(LocalDateTime.now().plusDays(7));

        p.edit("바이올린 구함", "바뀐 설명", Set.of(Instrument.VIOLIN), 1, "부산", "무보수", null);

        assertThat(p.getTitle()).isEqualTo("바이올린 구함");
        assertThat(p.getInstruments()).containsExactly(Instrument.VIOLIN);
        assertThat(p.getRecruitCount()).isEqualTo(1);
        assertThat(p.getDeadline()).isNull();
    }

    @Test
    void close하면_CLOSED이고_isClosed는_true() {
        RecruitmentPosting p = posting(LocalDateTime.now().plusDays(7));

        p.close();

        assertThat(p.getStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        assertThat(p.isClosed(LocalDateTime.now())).isTrue();
    }

    @Test
    void deadline이_지나면_OPEN이어도_isClosed는_true() {
        RecruitmentPosting past = posting(LocalDateTime.now().minusDays(1));
        RecruitmentPosting future = posting(LocalDateTime.now().plusDays(1));
        RecruitmentPosting always = posting(null);

        assertThat(past.isClosed(LocalDateTime.now())).isTrue();
        assertThat(future.isClosed(LocalDateTime.now())).isFalse();
        assertThat(always.isClosed(LocalDateTime.now())).isFalse();
    }

    @Test
    void delete하면_isDeleted가_true() {
        RecruitmentPosting p = posting(null);

        p.delete();

        assertThat(p.isDeleted()).isTrue();
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.entity.RecruitmentPostingTest" -q`
Expected: 컴파일 실패(`RecruitmentPosting` 없음).

- [ ] **Step 4: RecruitmentPosting 엔티티 구현**

`RecruitmentPosting.java`:
```java
package com.back.domain.recruitment.entity;

import com.back.domain.member.entity.Instrument;
import com.back.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구인 공고. 작성자는 원시 authorId(Long)로만 참조한다. 삭제는 soft delete. */
@Entity
@Table(name = "recruitment_posting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    /** 모집 파트(다중). 비어있지 않게 저장한다(검증은 요청 DTO에서). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recruitment_posting_instrument",
            joinColumns = @JoinColumn(name = "posting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument", length = 20)
    private Set<Instrument> instruments = new LinkedHashSet<>();

    /** 모집 인원. 미기재 허용. */
    private Integer recruitCount;

    @Column(length = 200)
    private String location;

    @Column(length = 200)
    private String fee;

    /** 모집 마감 일시. null 이면 상시 모집. */
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitmentStatus status;

    private LocalDateTime deletedAt;

    private RecruitmentPosting(Long authorId, String title, String description,
            Collection<Instrument> instruments, Integer recruitCount, String location, String fee,
            LocalDateTime deadline) {
        this.authorId = authorId;
        this.title = title;
        this.description = description;
        if (instruments != null) {
            this.instruments.addAll(instruments);
        }
        this.recruitCount = recruitCount;
        this.location = location;
        this.fee = fee;
        this.deadline = deadline;
        this.status = RecruitmentStatus.OPEN;
    }

    public static RecruitmentPosting create(Long authorId, String title, String description,
            Set<Instrument> instruments, Integer recruitCount, String location, String fee,
            LocalDateTime deadline) {
        return new RecruitmentPosting(authorId, title, description, instruments, recruitCount,
                location, fee, deadline);
    }

    /** 본문 필드를 전체 교체한다(PUT 시맨틱). 작성자·상태·삭제는 바꾸지 않는다. */
    public void edit(String title, String description, Set<Instrument> instruments,
            Integer recruitCount, String location, String fee, LocalDateTime deadline) {
        this.title = title;
        this.description = description;
        this.instruments.clear();
        if (instruments != null) {
            this.instruments.addAll(instruments);
        }
        this.recruitCount = recruitCount;
        this.location = location;
        this.fee = fee;
        this.deadline = deadline;
    }

    public void close() {
        this.status = RecruitmentStatus.CLOSED;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 마감 여부(파생): 수동 CLOSED 이거나 deadline 이 지났으면 마감. */
    public boolean isClosed(LocalDateTime now) {
        return status == RecruitmentStatus.CLOSED
                || (deadline != null && !now.isBefore(deadline));
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.entity.RecruitmentPostingTest" -q`
Expected: PASS (5개).

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentStatus.java BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentApplicationStatus.java BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentPosting.java BE/src/test/java/com/back/domain/recruitment/entity/RecruitmentPostingTest.java
git commit -m "feat: RECRUITMENT 공고 엔티티 및 상태 enum 추가

- RecruitmentStatus(OPEN/CLOSED), RecruitmentApplicationStatus(4상태)
- RecruitmentPosting(다중 악기 ElementCollection, soft delete, isClosed 파생)"
```

---

### Task 3: RecruitmentApplication 엔티티

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentApplication.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/entity/RecruitmentApplicationTest.java`

**Interfaces:**
- Consumes: `RecruitmentApplicationStatus`, `ErrorCode.RECRUITMENT_INVALID_APPLICATION_STATE`.
- Produces:
  - `RecruitmentApplication.apply(Long postingId, Long applicantId, String message) -> RecruitmentApplication` (PENDING 시작)
  - `application.accept()`, `application.reject()`, `application.withdraw()` — 각각 PENDING 에서만 전이, 아니면 `BusinessException(RECRUITMENT_INVALID_APPLICATION_STATE)`
  - getters: `getId, getPostingId, getApplicantId, getMessage, getStatus, getCreatedAt, getUpdatedAt`

- [ ] **Step 1: 실패하는 엔티티 테스트 작성**

`RecruitmentApplicationTest.java`:
```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.entity.RecruitmentApplicationTest" -q`
Expected: 컴파일 실패(`RecruitmentApplication` 없음).

- [ ] **Step 3: RecruitmentApplication 엔티티 구현**

`RecruitmentApplication.java`:
```java
package com.back.domain.recruitment.entity;

import com.back.global.common.BaseEntity;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구인 공고 지원. 공고·지원자는 원시 Long 으로 참조한다.
 * 상태 전이: PENDING → ACCEPTED/REJECTED(작성자) 또는 WITHDRAWN(지원자). PENDING 에서만 전이한다.
 */
@Entity
@Table(name = "recruitment_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postingId;

    @Column(nullable = false)
    private Long applicantId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitmentApplicationStatus status;

    private RecruitmentApplication(Long postingId, Long applicantId, String message) {
        this.postingId = postingId;
        this.applicantId = applicantId;
        this.message = message;
        this.status = RecruitmentApplicationStatus.PENDING;
    }

    public static RecruitmentApplication apply(Long postingId, Long applicantId, String message) {
        return new RecruitmentApplication(postingId, applicantId, message);
    }

    public void accept() {
        transition(RecruitmentApplicationStatus.ACCEPTED);
    }

    public void reject() {
        transition(RecruitmentApplicationStatus.REJECTED);
    }

    public void withdraw() {
        transition(RecruitmentApplicationStatus.WITHDRAWN);
    }

    private void transition(RecruitmentApplicationStatus next) {
        if (this.status != RecruitmentApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RECRUITMENT_INVALID_APPLICATION_STATE);
        }
        this.status = next;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.entity.RecruitmentApplicationTest" -q`
Expected: PASS (3개).

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/entity/RecruitmentApplication.java BE/src/test/java/com/back/domain/recruitment/entity/RecruitmentApplicationTest.java
git commit -m "feat: RECRUITMENT 지원 엔티티 추가

- RecruitmentApplication 상태머신(PENDING에서만 accept/reject/withdraw 전이)"
```

---

### Task 4: 리포지토리 2종

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/repository/RecruitmentPostingRepository.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/repository/RecruitmentApplicationRepository.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/repository/RecruitmentRepositoryTest.java`

**Interfaces:**
- Consumes: `RecruitmentPosting`, `RecruitmentApplication`, `RecruitmentStatus`, `RecruitmentApplicationStatus`, `Instrument`.
- Produces:
  - `RecruitmentPostingRepository`:
    - `Optional<RecruitmentPosting> findByIdAndDeletedAtIsNull(Long id)`
    - `Page<RecruitmentPosting> findOpen(RecruitmentStatus openStatus, LocalDateTime now, Instrument instrument, Pageable pageable)`
    - `Page<RecruitmentPosting> findClosed(RecruitmentStatus closedStatus, LocalDateTime now, Instrument instrument, Pageable pageable)`
    - `Page<RecruitmentPosting> findAllActive(Instrument instrument, Pageable pageable)`
  - `RecruitmentApplicationRepository`:
    - `boolean existsByPostingIdAndApplicantIdAndStatusIn(Long postingId, Long applicantId, Collection<RecruitmentApplicationStatus> statuses)`
    - `Page<RecruitmentApplication> findByPostingIdOrderByCreatedAtDescIdDesc(Long postingId, Pageable pageable)`
    - `Page<RecruitmentApplication> findByApplicantIdOrderByCreatedAtDescIdDesc(Long applicantId, Pageable pageable)`

- [ ] **Step 1: RecruitmentPostingRepository 작성**

`RecruitmentPostingRepository.java`:
```java
package com.back.domain.recruitment.repository;

import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitmentPostingRepository extends JpaRepository<RecruitmentPosting, Long> {

    Optional<RecruitmentPosting> findByIdAndDeletedAtIsNull(Long id);

    /** 모집중: 미삭제 + status=OPEN + (마감일 없음 or 아직 안 지남) + (악기 필터 없음 or 해당 악기 모집). 최신순. */
    @Query("select p from RecruitmentPosting p "
            + "where p.deletedAt is null and p.status = :openStatus "
            + "and (p.deadline is null or p.deadline > :now) "
            + "and (:instrument is null or :instrument member of p.instruments) "
            + "order by p.createdAt desc, p.id desc")
    Page<RecruitmentPosting> findOpen(@Param("openStatus") RecruitmentStatus openStatus,
            @Param("now") LocalDateTime now, @Param("instrument") Instrument instrument,
            Pageable pageable);

    /** 마감: 미삭제 + (status=CLOSED or 마감일 지남) + 악기 필터. 최신순. */
    @Query("select p from RecruitmentPosting p "
            + "where p.deletedAt is null "
            + "and (p.status = :closedStatus or (p.deadline is not null and p.deadline <= :now)) "
            + "and (:instrument is null or :instrument member of p.instruments) "
            + "order by p.createdAt desc, p.id desc")
    Page<RecruitmentPosting> findClosed(@Param("closedStatus") RecruitmentStatus closedStatus,
            @Param("now") LocalDateTime now, @Param("instrument") Instrument instrument,
            Pageable pageable);

    /** 전체(미삭제) + 악기 필터. 최신순. */
    @Query("select p from RecruitmentPosting p "
            + "where p.deletedAt is null "
            + "and (:instrument is null or :instrument member of p.instruments) "
            + "order by p.createdAt desc, p.id desc")
    Page<RecruitmentPosting> findAllActive(@Param("instrument") Instrument instrument,
            Pageable pageable);
}
```

- [ ] **Step 2: RecruitmentApplicationRepository 작성**

`RecruitmentApplicationRepository.java`:
```java
package com.back.domain.recruitment.repository;

import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentApplicationRepository
        extends JpaRepository<RecruitmentApplication, Long> {

    /** 활성 지원(PENDING/ACCEPTED) 유일성 판정용. */
    boolean existsByPostingIdAndApplicantIdAndStatusIn(Long postingId, Long applicantId,
            Collection<RecruitmentApplicationStatus> statuses);

    /** 공고별 지원자 목록(작성자용). 최신순, id 타이브레이크. */
    Page<RecruitmentApplication> findByPostingIdOrderByCreatedAtDescIdDesc(Long postingId,
            Pageable pageable);

    /** 내 지원 목록(지원자용). 최신순, id 타이브레이크. */
    Page<RecruitmentApplication> findByApplicantIdOrderByCreatedAtDescIdDesc(Long applicantId,
            Pageable pageable);
}
```

- [ ] **Step 3: 실패하는 리포지토리 테스트 작성**

`RecruitmentRepositoryTest.java`:
```java
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
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.repository.RecruitmentRepositoryTest" -q`
Expected: PASS (4개). (`member of` JPQL 이 H2 에서 동작하는지 함께 검증됨.)

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/repository/ BE/src/test/java/com/back/domain/recruitment/repository/RecruitmentRepositoryTest.java
git commit -m "feat: RECRUITMENT 리포지토리 추가

- 공고: scope별(open/closed/all) 조회 + 악기(member of) 필터, soft delete 필터
- 지원: 활성지원 존재 판정, 공고별/지원자별 페이징 목록"
```

---

### Task 5: 공고 DTO + RecruitmentPostingService

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/dto/RecruitmentPostingRequest.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/dto/RecruitmentPostingResponse.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/dto/RecruitmentScope.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/service/RecruitmentPostingService.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/service/RecruitmentPostingServiceTest.java`

**Interfaces:**
- Consumes: `RecruitmentPostingRepository`, `MemberQueryService.findDisplaysByIds(Set<Long>) -> Map<Long, MemberDisplay>`, `MemberDisplay(Long memberId, String nickname, boolean verified)`.
- Produces:
  - `record RecruitmentPostingRequest(String title, String description, Set<Instrument> instruments, Integer recruitCount, String location, String fee, LocalDateTime deadline)`
  - `record RecruitmentPostingResponse(Long id, MemberDisplay author, String title, String description, Set<Instrument> instruments, Integer recruitCount, String location, String fee, LocalDateTime deadline, RecruitmentStatus status, boolean closed, LocalDateTime createdAt, LocalDateTime updatedAt)`
  - `enum RecruitmentScope { OPEN, CLOSED, ALL }`
  - `RecruitmentPostingService`:
    - `RecruitmentPostingResponse register(Long authorId, RecruitmentPostingRequest request)`
    - `RecruitmentPostingResponse getPosting(Long id)`
    - `Page<RecruitmentPostingResponse> getPostings(RecruitmentScope scope, Instrument instrument, Pageable pageable)`
    - `RecruitmentPostingResponse editPosting(Long editorId, Long id, RecruitmentPostingRequest request)`
    - `RecruitmentPostingResponse closePosting(Long requesterId, Long id)`
    - `void deletePosting(Long requesterId, boolean requesterIsAdmin, Long id)`
    - `RecruitmentPosting findActive(Long id)` (package-private, Task 6 재사용) — 없으면 `RECRUITMENT_NOT_FOUND`

- [ ] **Step 1: DTO 3종 작성**

`RecruitmentScope.java`:
```java
package com.back.domain.recruitment.dto;

/** 구인 공고 목록 조회 범위. */
public enum RecruitmentScope {
    OPEN,
    CLOSED,
    ALL
}
```

`RecruitmentPostingRequest.java`:
```java
package com.back.domain.recruitment.dto;

import com.back.domain.member.entity.Instrument;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

/** 공고 등록/수정 공용 요청(전체 교체). */
public record RecruitmentPostingRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.") String title,
        @Size(max = 2000, message = "설명은 2000자를 넘을 수 없습니다.") String description,
        @NotEmpty(message = "모집 파트를 하나 이상 선택해 주세요.") Set<Instrument> instruments,
        @Min(value = 1, message = "모집 인원은 1명 이상이어야 합니다.") Integer recruitCount,
        @Size(max = 200, message = "활동 지역/장소는 200자를 넘을 수 없습니다.") String location,
        @Size(max = 200, message = "보수 안내는 200자를 넘을 수 없습니다.") String fee,
        LocalDateTime deadline) {
}
```

`RecruitmentPostingResponse.java`:
```java
package com.back.domain.recruitment.dto;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import java.time.LocalDateTime;
import java.util.Set;

public record RecruitmentPostingResponse(
        Long id,
        MemberDisplay author,
        String title,
        String description,
        Set<Instrument> instruments,
        Integer recruitCount,
        String location,
        String fee,
        LocalDateTime deadline,
        RecruitmentStatus status,
        boolean closed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RecruitmentPostingResponse of(RecruitmentPosting p, MemberDisplay author,
            LocalDateTime now) {
        return new RecruitmentPostingResponse(p.getId(), author, p.getTitle(), p.getDescription(),
                Set.copyOf(p.getInstruments()), p.getRecruitCount(), p.getLocation(), p.getFee(),
                p.getDeadline(), p.getStatus(), p.isClosed(now), p.getCreatedAt(), p.getUpdatedAt());
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`RecruitmentPostingServiceTest.java`:
```java
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
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.service.RecruitmentPostingServiceTest" -q`
Expected: 컴파일 실패(`RecruitmentPostingService` 없음).

- [ ] **Step 4: RecruitmentPostingService 구현**

`RecruitmentPostingService.java`:
```java
package com.back.domain.recruitment.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Instrument;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.recruitment.dto.RecruitmentPostingRequest;
import com.back.domain.recruitment.dto.RecruitmentPostingResponse;
import com.back.domain.recruitment.dto.RecruitmentScope;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import com.back.domain.recruitment.repository.RecruitmentPostingRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
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
 * 구인 공고 등록/조회/목록/수정/마감/삭제. 등록 자격은 인증 회원 누구나.
 * 작성자 표시정보는 MEMBER 협력(배치)으로 파생한다.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentPostingService {

    private final RecruitmentPostingRepository postingRepository;
    private final MemberQueryService memberQueryService;

    @Transactional
    public RecruitmentPostingResponse register(Long authorId, RecruitmentPostingRequest request) {
        RecruitmentPosting saved = postingRepository.save(RecruitmentPosting.create(authorId,
                request.title(), request.description(), request.instruments(),
                request.recruitCount(), request.location(), request.fee(), request.deadline()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecruitmentPostingResponse getPosting(Long id) {
        return toResponse(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<RecruitmentPostingResponse> getPostings(RecruitmentScope scope,
            Instrument instrument, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<RecruitmentPosting> page = switch (scope) {
            case OPEN -> postingRepository.findOpen(RecruitmentStatus.OPEN, now, instrument, pageable);
            case CLOSED -> postingRepository.findClosed(RecruitmentStatus.CLOSED, now, instrument,
                    pageable);
            case ALL -> postingRepository.findAllActive(instrument, pageable);
        };
        Set<Long> authorIds = page.getContent().stream()
                .map(RecruitmentPosting::getAuthorId).collect(Collectors.toSet());
        Map<Long, MemberDisplay> authors = memberQueryService.findDisplaysByIds(authorIds);
        return page.map(p -> RecruitmentPostingResponse.of(p, authors.get(p.getAuthorId()), now));
    }

    @Transactional
    public RecruitmentPostingResponse editPosting(Long editorId, Long id,
            RecruitmentPostingRequest request) {
        RecruitmentPosting posting = findActive(id);
        requireAuthor(posting, editorId);
        posting.edit(request.title(), request.description(), request.instruments(),
                request.recruitCount(), request.location(), request.fee(), request.deadline());
        return toResponse(posting);
    }

    @Transactional
    public RecruitmentPostingResponse closePosting(Long requesterId, Long id) {
        RecruitmentPosting posting = findActive(id);
        requireAuthor(posting, requesterId);
        posting.close();
        return toResponse(posting);
    }

    @Transactional
    public void deletePosting(Long requesterId, boolean requesterIsAdmin, Long id) {
        RecruitmentPosting posting = findActive(id);
        if (!posting.getAuthorId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        posting.delete();
    }

    /** 미삭제 공고 조회. 지원 서비스(Task 6)에서 재사용한다. 없으면 RECRUITMENT_NOT_FOUND. */
    @Transactional(readOnly = true)
    public RecruitmentPosting findActive(Long id) {
        return postingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
    }

    private void requireAuthor(RecruitmentPosting posting, Long requesterId) {
        if (!posting.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private RecruitmentPostingResponse toResponse(RecruitmentPosting posting) {
        MemberDisplay author = memberQueryService
                .findDisplaysByIds(Set.of(posting.getAuthorId()))
                .get(posting.getAuthorId());
        return RecruitmentPostingResponse.of(posting, author, LocalDateTime.now());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.service.RecruitmentPostingServiceTest" -q`
Expected: PASS (5개).

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/dto/ BE/src/main/java/com/back/domain/recruitment/service/RecruitmentPostingService.java BE/src/test/java/com/back/domain/recruitment/service/RecruitmentPostingServiceTest.java
git commit -m "feat: RECRUITMENT 공고 서비스 및 DTO 추가

- 등록(인증 회원 누구나)/조회/목록(scope+악기 필터)/수정/마감/삭제(작성자 또는 어드민)
- 작성자 표시정보 MEMBER 배치 협력 파생(N+1 없음)"
```

---

### Task 6: 지원 DTO + RecruitmentApplicationService

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/dto/ApplyRecruitmentRequest.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/dto/RecruitmentApplicationResponse.java`
- Create: `BE/src/main/java/com/back/domain/recruitment/service/RecruitmentApplicationService.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/service/RecruitmentApplicationServiceTest.java`

**Interfaces:**
- Consumes: `RecruitmentApplicationRepository`, `RecruitmentPostingService.findActive(Long) -> RecruitmentPosting`, `MemberQueryService.findDisplaysByIds`, `RecruitmentPosting.getAuthorId()/isClosed(now)`.
- Produces:
  - `record ApplyRecruitmentRequest(String message)`
  - `record RecruitmentApplicationResponse(Long id, Long postingId, MemberDisplay applicant, String message, RecruitmentApplicationStatus status, LocalDateTime createdAt, LocalDateTime updatedAt)`
  - `RecruitmentApplicationService`:
    - `RecruitmentApplicationResponse apply(Long applicantId, Long postingId, ApplyRecruitmentRequest request)`
    - `Page<RecruitmentApplicationResponse> getApplicationsForPosting(Long requesterId, Long postingId, Pageable pageable)`
    - `Page<RecruitmentApplicationResponse> getMyApplications(Long applicantId, Pageable pageable)`
    - `RecruitmentApplicationResponse accept(Long requesterId, Long applicationId)`
    - `RecruitmentApplicationResponse reject(Long requesterId, Long applicationId)`
    - `RecruitmentApplicationResponse withdraw(Long requesterId, Long applicationId)`

- [ ] **Step 1: DTO 2종 작성**

`ApplyRecruitmentRequest.java`:
```java
package com.back.domain.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공고 지원 요청. 어느 파트에 지원하는지 등을 자유 기술한다. */
public record ApplyRecruitmentRequest(
        @NotBlank(message = "지원 메시지를 입력해 주세요.")
        @Size(max = 1000, message = "지원 메시지는 1000자를 넘을 수 없습니다.") String message) {
}
```

`RecruitmentApplicationResponse.java`:
```java
package com.back.domain.recruitment.dto;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import java.time.LocalDateTime;

public record RecruitmentApplicationResponse(
        Long id,
        Long postingId,
        MemberDisplay applicant,
        String message,
        RecruitmentApplicationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RecruitmentApplicationResponse of(RecruitmentApplication a,
            MemberDisplay applicant) {
        return new RecruitmentApplicationResponse(a.getId(), a.getPostingId(), applicant,
                a.getMessage(), a.getStatus(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`RecruitmentApplicationServiceTest.java`:
```java
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
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.service.RecruitmentApplicationServiceTest" -q`
Expected: 컴파일 실패(`RecruitmentApplicationService` 없음).

- [ ] **Step 4: RecruitmentApplicationService 구현**

`RecruitmentApplicationService.java`:
```java
package com.back.domain.recruitment.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.recruitment.dto.ApplyRecruitmentRequest;
import com.back.domain.recruitment.dto.RecruitmentApplicationResponse;
import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.repository.RecruitmentApplicationRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 지원 생성/조회/상태전이. 활성 지원(PENDING/ACCEPTED) 유일.
 * 지원자 표시정보는 MEMBER 협력(배치)으로 파생한다.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentApplicationService {

    private static final List<RecruitmentApplicationStatus> ACTIVE_STATUSES =
            List.of(RecruitmentApplicationStatus.PENDING, RecruitmentApplicationStatus.ACCEPTED);

    private final RecruitmentApplicationRepository applicationRepository;
    private final RecruitmentPostingService postingService;
    private final MemberQueryService memberQueryService;

    @Transactional
    public RecruitmentApplicationResponse apply(Long applicantId, Long postingId,
            ApplyRecruitmentRequest request) {
        RecruitmentPosting posting = postingService.findActive(postingId);
        if (posting.getAuthorId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPLY_OWN_RECRUITMENT);
        }
        if (posting.isClosed(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RECRUITMENT_CLOSED);
        }
        if (applicationRepository.existsByPostingIdAndApplicantIdAndStatusIn(postingId, applicantId,
                ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
        RecruitmentApplication saved = applicationRepository.save(
                RecruitmentApplication.apply(postingId, applicantId, request.message()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<RecruitmentApplicationResponse> getApplicationsForPosting(Long requesterId,
            Long postingId, Pageable pageable) {
        RecruitmentPosting posting = postingService.findActive(postingId);
        if (!posting.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toResponsePage(applicationRepository
                .findByPostingIdOrderByCreatedAtDescIdDesc(postingId, pageable));
    }

    @Transactional(readOnly = true)
    public Page<RecruitmentApplicationResponse> getMyApplications(Long applicantId,
            Pageable pageable) {
        return toResponsePage(applicationRepository
                .findByApplicantIdOrderByCreatedAtDescIdDesc(applicantId, pageable));
    }

    @Transactional
    public RecruitmentApplicationResponse accept(Long requesterId, Long applicationId) {
        RecruitmentApplication application = findApplicationOwnedByPostingAuthor(requesterId,
                applicationId);
        application.accept();
        return toResponse(application);
    }

    @Transactional
    public RecruitmentApplicationResponse reject(Long requesterId, Long applicationId) {
        RecruitmentApplication application = findApplicationOwnedByPostingAuthor(requesterId,
                applicationId);
        application.reject();
        return toResponse(application);
    }

    @Transactional
    public RecruitmentApplicationResponse withdraw(Long requesterId, Long applicationId) {
        RecruitmentApplication application = findApplication(applicationId);
        if (!application.getApplicantId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        application.withdraw();
        return toResponse(application);
    }

    /** 수락/거절은 공고 작성자만. 지원 → 공고 → 작성자 대조. */
    private RecruitmentApplication findApplicationOwnedByPostingAuthor(Long requesterId,
            Long applicationId) {
        RecruitmentApplication application = findApplication(applicationId);
        RecruitmentPosting posting = postingService.findActive(application.getPostingId());
        if (!posting.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return application;
    }

    private RecruitmentApplication findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECRUITMENT_APPLICATION_NOT_FOUND));
    }

    private RecruitmentApplicationResponse toResponse(RecruitmentApplication application) {
        MemberDisplay applicant = memberQueryService
                .findDisplaysByIds(Set.of(application.getApplicantId()))
                .get(application.getApplicantId());
        return RecruitmentApplicationResponse.of(application, applicant);
    }

    private Page<RecruitmentApplicationResponse> toResponsePage(Page<RecruitmentApplication> page) {
        Set<Long> applicantIds = page.getContent().stream()
                .map(RecruitmentApplication::getApplicantId).collect(Collectors.toSet());
        Map<Long, MemberDisplay> applicants = memberQueryService.findDisplaysByIds(applicantIds);
        return page.map(a -> RecruitmentApplicationResponse.of(a, applicants.get(a.getApplicantId())));
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.service.RecruitmentApplicationServiceTest" -q`
Expected: PASS (7개).

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/dto/ApplyRecruitmentRequest.java BE/src/main/java/com/back/domain/recruitment/dto/RecruitmentApplicationResponse.java BE/src/main/java/com/back/domain/recruitment/service/RecruitmentApplicationService.java BE/src/test/java/com/back/domain/recruitment/service/RecruitmentApplicationServiceTest.java
git commit -m "feat: RECRUITMENT 지원 서비스 및 DTO 추가

- 지원(본인공고/마감/중복 게이팅), 지원자목록(작성자만)/내지원 목록
- 수락·거절(작성자)·철회(지원자), 활성지원 유일·거절 후 재지원 허용"
```

---

### Task 7: RecruitmentPostingController (공고 API)

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/controller/RecruitmentPostingController.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/controller/RecruitmentPostingControllerTest.java`

**Interfaces:**
- Consumes: `RecruitmentPostingService`, `RecruitmentScope`, `Instrument`, `ApiResponse`.
- Produces: HTTP 엔드포인트 `/api/recruitments` (POST/GET/GET{id}/PUT{id}/POST{id}/close/DELETE{id}). 어드민 판정 `isAdmin(Authentication)` 로컬 static.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`RecruitmentPostingControllerTest.java`:
```java
package com.back.domain.recruitment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RecruitmentPostingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;

    private String authorBearer;
    private String otherBearer;
    private String adminBearer;

    private static final String BODY = "{\"title\":\"첼로 구함\",\"description\":\"설명\","
            + "\"instruments\":[\"CELLO\",\"VIOLA\"],\"recruitCount\":2,\"location\":\"서울\","
            + "\"fee\":\"회당 5만원\",\"deadline\":\"2026-12-01T19:30:00\"}";

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.createLocal("author", "pw", "a@x.com", "작성자"));
        authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), Role.USER);
        Member other = memberRepository.save(Member.createLocal("other", "pw", "o@x.com", "타인"));
        otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), Role.USER);
        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
    }

    private String register(String bearer) throws Exception {
        String json = mockMvc.perform(post("/api/recruitments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 토큰_없이_등록은_401() throws Exception {
        mockMvc.perform(post("/api/recruitments")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증회원은_등록하고_목록에_보인다() throws Exception {
        register(authorBearer);
        mockMvc.perform(get("/api/recruitments").param("scope", "OPEN")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("첼로 구함"))
                .andExpect(jsonPath("$.data.content[0].author.nickname").value("작성자"))
                .andExpect(jsonPath("$.data.content[0].closed").value(false));
    }

    @Test
    void 악기필터로_목록을_거른다() throws Exception {
        register(authorBearer);
        mockMvc.perform(get("/api/recruitments").param("scope", "OPEN").param("instrument", "VIOLIN")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void 모집파트_누락은_400() throws Exception {
        mockMvc.perform(post("/api/recruitments").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"instruments\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 타인_수정은_403() throws Exception {
        String id = register(authorBearer);
        mockMvc.perform(put("/api/recruitments/" + id).header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 작성자는_마감할_수_있다() throws Exception {
        String id = register(authorBearer);
        mockMvc.perform(post("/api/recruitments/" + id + "/close")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closed").value(true));
    }

    @Test
    void 어드민은_타인_공고를_삭제할_수_있다() throws Exception {
        String id = register(authorBearer);
        mockMvc.perform(delete("/api/recruitments/" + id).header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recruitments/" + id).header("Authorization", authorBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-08"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.controller.RecruitmentPostingControllerTest" -q`
Expected: 컴파일/404 실패(컨트롤러 없음).

- [ ] **Step 3: RecruitmentPostingController 구현**

`RecruitmentPostingController.java`:
```java
package com.back.domain.recruitment.controller;

import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.dto.RecruitmentPostingRequest;
import com.back.domain.recruitment.dto.RecruitmentPostingResponse;
import com.back.domain.recruitment.dto.RecruitmentScope;
import com.back.domain.recruitment.service.RecruitmentPostingService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 구인 공고 API. 모두 인증 필요(principal = memberId).
 * 등록은 인증 회원 누구나, 모더레이션 삭제는 역할로 판정 — 어드민 전용 경로 없음.
 */
@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentPostingController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final RecruitmentPostingService postingService;

    @PostMapping
    public ApiResponse<RecruitmentPostingResponse> register(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RecruitmentPostingRequest request) {
        return ApiResponse.success(postingService.register(memberId, request));
    }

    @GetMapping
    public ApiResponse<Page<RecruitmentPostingResponse>> list(
            @RequestParam(defaultValue = "OPEN") RecruitmentScope scope,
            @RequestParam(required = false) Instrument instrument,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(postingService.getPostings(scope, instrument,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecruitmentPostingResponse> get(@PathVariable Long id) {
        return ApiResponse.success(postingService.getPosting(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecruitmentPostingResponse> edit(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id, @Valid @RequestBody RecruitmentPostingRequest request) {
        return ApiResponse.success(postingService.editPosting(memberId, id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<RecruitmentPostingResponse> close(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(postingService.closePosting(memberId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        postingService.deletePosting(memberId, isAdmin(authentication), id);
        return ApiResponse.success();
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

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.controller.RecruitmentPostingControllerTest" -q`
Expected: PASS (7개).

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/controller/RecruitmentPostingController.java BE/src/test/java/com/back/domain/recruitment/controller/RecruitmentPostingControllerTest.java
git commit -m "feat: RECRUITMENT 공고 컨트롤러 추가

- POST/GET(scope+악기 필터)/GET{id}/PUT{id}/POST close/DELETE{id}
- 등록=인증 회원, 수정·마감=작성자, 삭제=작성자 또는 어드민"
```

---

### Task 8: RecruitmentApplicationController (지원 API)

**Files:**
- Create: `BE/src/main/java/com/back/domain/recruitment/controller/RecruitmentApplicationController.java`
- Test: `BE/src/test/java/com/back/domain/recruitment/controller/RecruitmentApplicationControllerTest.java`

**Interfaces:**
- Consumes: `RecruitmentApplicationService`, `ApiResponse`.
- Produces: HTTP 엔드포인트 — `POST /api/recruitments/{id}/applications`, `GET /api/recruitments/{id}/applications`, `GET /api/recruitments/applications/me`, `POST /api/recruitments/applications/{applicationId}/accept|reject|withdraw`.

**주의(라우팅):** `GET /api/recruitments/applications/me` 와 `GET /api/recruitments/{id}/applications` 가 공존한다. Spring MVC 는 더 구체적인 리터럴 경로(`applications/me`)를 `{id}` 보다 우선 매칭하므로 충돌하지 않는다. 두 엔드포인트를 이 컨트롤러에 함께 둔다.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`RecruitmentApplicationControllerTest.java`:
```java
package com.back.domain.recruitment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RecruitmentApplicationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;

    private String authorBearer;
    private String applicantBearer;

    private static final String POSTING_BODY = "{\"title\":\"첼로 구함\",\"description\":\"설명\","
            + "\"instruments\":[\"CELLO\"],\"recruitCount\":1,\"deadline\":\"2026-12-01T19:30:00\"}";
    private static final String APPLY_BODY = "{\"message\":\"첼로 지원합니다\"}";

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.createLocal("author", "pw", "a@x.com", "작성자"));
        authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), Role.USER);
        Member applicant = memberRepository.save(
                Member.createLocal("app", "pw", "p@x.com", "지원자"));
        applicantBearer = "Bearer " + jwtProvider.createAccessToken(applicant.getId(), Role.USER);
    }

    private String createPosting() throws Exception {
        String json = mockMvc.perform(post("/api/recruitments").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(POSTING_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    private String apply(String postingId) throws Exception {
        String json = mockMvc.perform(post("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", applicantBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 지원하면_PENDING으로_생성된다() throws Exception {
        String postingId = createPosting();
        mockMvc.perform(post("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", applicantBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.applicant.nickname").value("지원자"));
    }

    @Test
    void 본인공고_지원은_409() throws Exception {
        String postingId = createPosting();
        mockMvc.perform(post("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.resultCode").value("409-09"));
    }

    @Test
    void 작성자는_지원자목록을_보고_수락한다() throws Exception {
        String postingId = createPosting();
        String applicationId = apply(postingId);

        mockMvc.perform(get("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].message").value("첼로 지원합니다"));

        mockMvc.perform(post("/api/recruitments/applications/" + applicationId + "/accept")
                        .header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void 지원자는_내지원목록을_보고_철회한다() throws Exception {
        String postingId = createPosting();
        String applicationId = apply(postingId);

        mockMvc.perform(get("/api/recruitments/applications/me")
                        .header("Authorization", applicantBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));

        mockMvc.perform(post("/api/recruitments/applications/" + applicationId + "/withdraw")
                        .header("Authorization", applicantBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void 타인이_지원자목록조회는_403() throws Exception {
        String postingId = createPosting();
        apply(postingId);

        mockMvc.perform(get("/api/recruitments/" + postingId + "/applications")
                        .header("Authorization", applicantBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.controller.RecruitmentApplicationControllerTest" -q`
Expected: 컴파일/404 실패(컨트롤러 없음).

- [ ] **Step 3: RecruitmentApplicationController 구현**

`RecruitmentApplicationController.java`:
```java
package com.back.domain.recruitment.controller;

import com.back.domain.recruitment.dto.ApplyRecruitmentRequest;
import com.back.domain.recruitment.dto.RecruitmentApplicationResponse;
import com.back.domain.recruitment.service.RecruitmentApplicationService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구인 공고 지원 API. 모두 인증 필요(principal = memberId).
 * 지원=인증 회원(본인 공고 제외), 지원자 목록·수락·거절=공고 작성자, 철회=지원자 본인.
 */
@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentApplicationController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final RecruitmentApplicationService applicationService;

    @PostMapping("/{id}/applications")
    public ApiResponse<RecruitmentApplicationResponse> apply(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id,
            @Valid @RequestBody ApplyRecruitmentRequest request) {
        return ApiResponse.success(applicationService.apply(memberId, id, request));
    }

    @GetMapping("/{id}/applications")
    public ApiResponse<Page<RecruitmentApplicationResponse>> listForPosting(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(applicationService.getApplicationsForPosting(memberId, id,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @GetMapping("/applications/me")
    public ApiResponse<Page<RecruitmentApplicationResponse>> listMine(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(applicationService.getMyApplications(memberId,
                PageRequest.of(Math.max(page, 0), clamp(size))));
    }

    @PostMapping("/applications/{applicationId}/accept")
    public ApiResponse<RecruitmentApplicationResponse> accept(
            @AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.accept(memberId, applicationId));
    }

    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<RecruitmentApplicationResponse> reject(
            @AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.reject(memberId, applicationId));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public ApiResponse<RecruitmentApplicationResponse> withdraw(
            @AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.withdraw(memberId, applicationId));
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd BE && ./gradlew test --tests "com.back.domain.recruitment.controller.RecruitmentApplicationControllerTest" -q`
Expected: PASS (5개).

- [ ] **Step 5: 전체 회귀 테스트**

Run: `cd BE && ./gradlew test -q`
Expected: BUILD SUCCESSFUL (RECRUITMENT 신규 + 기존 전 도메인 모두 통과).

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/domain/recruitment/controller/RecruitmentApplicationController.java BE/src/test/java/com/back/domain/recruitment/controller/RecruitmentApplicationControllerTest.java
git commit -m "feat: RECRUITMENT 지원 컨트롤러 추가

- 지원(POST {id}/applications)·지원자목록(작성자)·내지원목록(applications/me)
- 수락·거절(작성자)·철회(지원자) 상태전이 엔드포인트"
```

---

## 마무리(플랜 실행 후)

- `docs/TODO-BACKLOG.md` 의 RECRUITMENT 항목을 완료로 옮기고 `docs/TODO-DONE.md` 에 기록.
- `docs/CONTEXT.md` 현재 상태·주의에 RECRUITMENT 반영(구현 도메인 목록, API 요약, 에러코드).
- `docs/AI-ACTION-LOGS.md` 에 작업 로그 추가.
- (선택) `docs/DOMAIN-RECRUITMENT-STATUTE.md` 의 "구현 착수 시 확정한 결정" 절에 실제 확정 사항 기록(applicationCount 응답 미포함, isAdmin 로컬 static, PageImpl 직렬화 공통 DTO는 BACKLOG 등).
- 커밋 완료 후 사용자에게 푸시 여부 확인(푸시는 사용자 요청 시에만).

## 범위 밖(이 플랜에서 다루지 않음 — spec §10)

구직 공고, PERFORMANCE 연결, 지원 첨부파일, 지원자↔작성자 메시징/CHAT 연계, 키워드/태그 검색, 알림, 마감 자동화 배치, `applicationCount` 응답 필드, 공통 `PageResponse<T>` DTO(코드베이스 공통 BACKLOG).
