# TIL — `@ElementCollection`의 N+1을 어노테이션 한 줄(`@BatchSize`)로 잡기

> 작성일: 2026-07-23
> 목적: 목록 응답에서 컬렉션 필드가 만드는 N+1을 **왜** 생기고 **어떻게** `@BatchSize`로 잡는지, 그리고 대안(전역 설정·fetch join·EntityGraph)과의 선택 기준을 이 문서 하나로 이해한다.
> 관련 코드: `domain.recruitment.entity.RecruitmentPosting.instruments` / 규칙: `docs/DOMAIN-RECRUITMENT-STATUTE.md §5`

---

## 1. 배경 — 구인 공고의 "모집 악기"는 다중 값

구인 공고 하나가 여러 파트를 모집할 수 있어서, 악기를 컬렉션으로 저장했다.

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(name = "recruitment_posting_instrument",
        joinColumns = @JoinColumn(name = "posting_id"))
@Enumerated(EnumType.STRING)
@Column(name = "instrument", length = 20)
private Set<Instrument> instruments = new LinkedHashSet<>();
```

`@ElementCollection`은 값 타입(엔티티가 아닌 것 — 여기선 enum) 묶음을 **별도 테이블**(`recruitment_posting_instrument`)에 저장한다. 공고 테이블과 1:N 관계이지만, 반대편이 독립 엔티티가 아니라 "공고에 종속된 값"이다.

---

## 2. 문제 — 목록 응답에서 조용히 생기는 N+1

목록 조회는 이렇게 흐른다.

```java
Page<RecruitmentPosting> page = postingRepository.findOpen(...);   // 쿼리 1번: 공고 N건
// ... 응답으로 매핑하면서 각 공고의 instruments 를 읽음
page.map(p -> RecruitmentPostingResponse.of(p, author, now));      // of() 안에서 p.getInstruments()
```

`instruments`는 `LAZY`다. 그래서 **응답을 만들며 `getInstruments()`를 건드리는 순간마다** 컬렉션 초기화 쿼리가 나간다.

```
SELECT ... FROM recruitment_posting WHERE ...        -- 1번 (공고 N건)
SELECT ... FROM recruitment_posting_instrument WHERE posting_id = 1   -- +1
SELECT ... FROM recruitment_posting_instrument WHERE posting_id = 2   -- +1
...                                                                    -- N번 반복
```

공고 N건이면 **1 + N번** 쿼리. 이게 N+1이다.

주의할 점: 작성자 표시정보(닉네임·인증뱃지)의 N+1은 이미 `MemberQueryService.findDisplaysByIds`로 **한 번에 IN 조회**해서 막아뒀다. 그런데 그건 "다른 도메인(MEMBER) 협력"의 N+1이고, `instruments`는 **같은 엔티티 안의 컬렉션 필드**라 그 배치 조회가 커버하지 못한다. 최종 리뷰에서 이걸 잡아냈다. N+1은 한 곳만 막았다고 끝이 아니라 **경로마다** 봐야 한다.

---

## 3. 해결 — `@BatchSize` 한 줄

필드에 `@org.hibernate.annotations.BatchSize`를 붙였다.

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(name = "recruitment_posting_instrument", ...)
@BatchSize(size = 100)          // ← 이 한 줄
@Enumerated(EnumType.STRING)
@Column(name = "instrument", length = 20)
private Set<Instrument> instruments = new LinkedHashSet<>();
```

원리: 첫 번째 공고의 `instruments`를 초기화할 때, Hibernate가 **아직 초기화 안 된 다른 공고들의 id를 최대 100개까지 모아** 한 번의 `IN` 쿼리로 같이 로딩한다.

```
SELECT ... FROM recruitment_posting_instrument
WHERE posting_id IN (1, 2, 3, ..., 100)      -- 한 방에
```

- 목록 size 상한이 50이므로, 한 페이지는 **컬렉션 조회 1번**으로 끝난다(1 + N → 1 + ceil(N/100)).
- 코드 변경은 어노테이션 한 줄. 쿼리를 손대거나 DTO 매핑을 바꿀 필요가 없다.

---

## 4. 왜 `@BatchSize`를 골랐나 — 대안과 비교

N+1을 잡는 방법은 여러 가지다. 이번엔 왜 `@BatchSize`였는지.

| 방법 | 하는 일 | 이번에 안 쓴 이유 |
|---|---|---|
| **`@BatchSize(size=n)`** (채택) | 미초기화 컬렉션을 id 묶음으로 IN 배치 로딩 | 스코프가 이 필드에만 한정, 페이징과 함께 안전, 한 줄 |
| 전역 `hibernate.default_batch_fetch_size` | 앱 전체 모든 지연 컬렉션/프록시에 배치 적용 | 효과는 크지만 **전역 부작용**. 이번 브랜치 범위를 넘어섬 → 도입은 별도 결정 |
| `join fetch` / `@EntityGraph` | 목록 쿼리에서 컬렉션까지 조인해 한 방에 | 컬렉션 조인 + **페이징**은 위험: DB가 조인으로 행을 뻥튀기한 뒤 메모리에서 페이징(`HHH000104` 경고), 컬렉션 2개 이상 조인 시 `MultipleBagFetchException` |
| DTO 프로젝션 별도 조회 | 필요한 필드만 별도 쿼리로 조립 | 과설계. 이 규모엔 배치 한 줄이 적절(YAGNI) |

핵심 판단: **"페이징이 걸린 목록에서 컬렉션을 join fetch 하면 안 된다"**. 조인은 1:N에서 행 수를 늘려 `LIMIT`이 엉키기 때문. 그래서 페이징 목록의 컬렉션 N+1은 조인이 아니라 **배치 로딩(`@BatchSize` / `default_batch_fetch_size`)**으로 푸는 게 정석이다.

---

## 5. 이번 작업에서 함께 배운 것 (곁가지 학습)

같은 도메인을 만들며 마주친, N+1과 짝이 되는 JPA 함정들.

- **JPQL `member of`로 컬렉션 원소 필터링**: "이 악기를 모집하는 공고"를 `:instrument member of p.instruments`로 걸렀다. 컬렉션에 특정 값이 들어있는지를 조건으로 쓰는 문법. enum 상태값은 FQN 리터럴 대신 `@Param`으로 바인딩해야 리네임에 안전하다.
- **`LocalDateTime.now()` 경계 테스트의 비결정성**: `deadline == now`를 검증하는 테스트가 간헐 실패했다. `now()`의 초 이하(나노초) 정밀도가 저장 컬럼 ↔ JDBC 파라미터 바인딩 사이에서 **동일하게 왕복하지 않아서**였다. 고정 리터럴(`LocalDateTime.of(2026,6,1,12,0,0)`)로 바꿔 결정적으로 만들었다. 교훈: **경계(==) 테스트에 벽시계 시간을 쓰지 말 것.**
- **`open-in-view: false`와 지연 로딩 위치**: 이 프로젝트는 OSIV를 껐다. 그래서 지연 컬렉션 접근이 **반드시 `@Transactional` 서비스 안**에서 일어나야 한다(응답 직렬화 시점엔 세션이 닫혀 `LazyInitializationException`). 우리 매핑은 서비스 안에서 끝나므로 안전.

---

## 6. 엔지니어로서 더 공부할 것 (study pointers)

이 한 번의 수정 뒤에 있는, 파고들면 좋은 주제들.

1. **N+1의 세 가지 해법의 트레이드오프를 직접 재현해보기**
   - `spring.jpa.show-sql=true` + `p6spy`로 실제 쿼리 수를 눈으로 확인.
   - 같은 목록을 (a) 기본 LAZY, (b) `@BatchSize`, (c) `join fetch`로 각각 짜고 쿼리 수·행 수·정확성을 비교.
2. **`@BatchSize`의 배치 전략**: Hibernate가 배치 크기를 어떻게 쪼개는지(옛 버전의 지수 배치 vs 최신의 IN 패딩), `in_clause_parameter_padding`이 쿼리 플랜 캐시에 주는 영향.
3. **페이징 + 컬렉션 페치의 함정**: `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory` 경고를 실제로 띄워보고, 왜 위험한지(전체 로딩 후 메모리 페이징) 체감. `MultipleBagFetchException`이 왜 나는지(`List` = bag 2개 조인).
4. **`@ElementCollection` vs 별도 엔티티(1:N `@OneToMany`)의 선택 기준**: 값 타입은 생명주기가 부모에 종속(전체 삭제·재삽입 특성). 원소를 독립적으로 조회/수정/참조해야 하면 엔티티로 승격. 이번 악기는 "공고에 종속된 값"이라 ElementCollection이 맞았다.
5. **`open-in-view`(OSIV) 논쟁**: 켜면 편하지만 DB 커넥션을 뷰 렌더까지 붙잡아 커넥션 풀 고갈 위험. 끄면 지연 로딩을 서비스 경계 안으로 강제 → 성능은 예측 가능해지지만 설계 부담이 는다. 왜 프로덕션에서 끄는 쪽을 권장하는지.
6. **DTO 프로젝션 & `@EntityGraph`**: 읽기 전용 조회에서 엔티티 그래프를 명시적으로 제어하는 법. 언제 프로젝션(인터페이스/클래스 기반)으로 아예 필요한 필드만 뽑는 게 나은지.
7. **측정 습관**: N+1은 "돌아는 간다"라서 테스트로는 안 잡힌다(우리도 최종 리뷰가 코드 읽기로 잡음). 통합 테스트에서 발생 쿼리 수를 단언하는 도구(`datasource-proxy`, Hibernate `Statistics.getQueryExecutionCount`)로 **회귀를 자동 감지**하는 습관.

---

## 7. 한 줄 정리

- 목록에서 지연 컬렉션 필드를 매핑하면 **행마다** 초기화 쿼리 → N+1. 다른 도메인 협력의 N+1을 막았어도 **엔티티 내부 컬렉션은 별개**다.
- 페이징 목록의 컬렉션 N+1은 join fetch(행 뻥튀기·`LIMIT` 붕괴)가 아니라 **배치 로딩**으로 푼다.
- 스코프를 필드에 한정하고 싶으면 `@BatchSize(size=n)`, 앱 전체면 `default_batch_fetch_size`. 이번엔 부작용 없는 `@BatchSize` 한 줄.
- N+1은 "정상 동작"으로 위장하므로, **쿼리 수를 단언**하는 습관으로 회귀를 잡아라.
