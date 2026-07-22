# DOMAIN-RECRUITMENT-STATUTE

구인(연주자 모집) 도메인 구현 규칙.

> 작성일: 2026-07-22. 기준 spec: `docs/superpowers/specs/2026-07-22-recruitment-design.md`.
> 등록 자격·soft delete·상태머신·도메인 경계·페이징은 확정 규칙이다. 세부 시그니처는 구현 태스크에서 확정하며, 변경 시 이 문서를 갱신한다.

---

## 1. 패키지

```
com.back.domain.recruitment
├── controller
├── service
├── repository
├── entity
└── dto
```

---

## 2. 엔티티

`BaseEntity` 상속(createdAt/updatedAt). 기존 패턴 준수: `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static 팩토리, 단순 접근자는 Lombok `@Getter`, setter 금지(의도가 드러나는 변경 메서드).

### 2.1 RecruitmentPosting (soft delete)

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `authorId` | Long | 공고 작성자. non-null |
| `title` | String | non-null, ≤100 |
| `description` | String | 모집 상세. ≤2000, nullable |
| `instruments` | Set\<Instrument\> | 모집 파트(다중). `@ElementCollection`, 비어있지 않음 |
| `recruitCount` | Integer | 모집 인원. nullable(미기재 허용) |
| `location` | String | 활동 지역/장소. ≤200, nullable |
| `fee` | String | 보수/페이 안내(자유 텍스트, 예: "회당 5만원", "무보수"). ≤200, nullable |
| `deadline` | LocalDateTime | 모집 마감 일시. nullable(상시 모집 허용) |
| `status` | enum(`OPEN`/`CLOSED`) | 기본 OPEN |
| `deletedAt` | LocalDateTime | soft delete 마킹. null=미삭제 |

* `Instrument`는 MEMBER 프로필의 enum 21종을 **재사용**(어휘 통일, 악기별 필터/매칭). 도메인 간 연관 매핑이 아닌 값(enum) 재사용이므로 경계 위반이 아니다.
* 팩토리: `create(authorId, title, description, instruments, recruitCount, location, fee, deadline)`.
* 변경 메서드: `edit(...)`(본문 전체 교체, PUT 시맨틱), `close()`(status→CLOSED), `delete()`(deletedAt=now), `isDeleted()`.
* 파생 판정: `isClosed(now)` = `status == CLOSED || (deadline != null && now >= deadline)`. 상태 필드 자체는 수동 전환만 한다(자동으로 CLOSED로 바꾸지 않음).
* `authorId`는 원시 Long. 작성자 정보가 필요하면 서비스 계층에서 MEMBER 서비스로 협력한다.

### 2.2 RecruitmentApplication (soft delete 없음 — 철회는 상태)

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `postingId` | Long | 대상 공고. non-null |
| `applicantId` | Long | 지원자. non-null |
| `message` | String | 지원 메시지(어느 파트 지원인지 등 자유 기술). ≤1000, non-null |
| `status` | enum(`PENDING`/`ACCEPTED`/`REJECTED`/`WITHDRAWN`) | 기본 PENDING |

* 다중 악기 공고에서 "어느 파트에 지원하는지"는 `message` 텍스트로 받는다(구조화하지 않음).
* 팩토리: `apply(postingId, applicantId, message)`(PENDING 시작).
* 변경 메서드: `accept()`, `reject()`, `withdraw()` — 각각 **PENDING에서만 전이**, 아니면 `BusinessException(RECRUITMENT_INVALID_APPLICATION_STATE)`.

---

## 3. 등록 자격 & 권한 규칙 (확정)

### 3.1 공고(Posting)

| 행위 | 자격 | 위반 시 |
|---|---|---|
| 등록 | **인증 회원 누구나**(PERFORMANCE와 달리 인증 연주자 게이팅 없음) | — |
| 수정 | 작성자 본인 | `FORBIDDEN`(403-01) |
| 마감(CLOSED) | 작성자 본인 | `FORBIDDEN`(403-01) |
| 삭제(soft) | 작성자 또는 `ROLE_ADMIN`(모더레이션) | `FORBIDDEN`(403-01) |
| 조회·목록 | 인증 회원 누구나 | — |

* 어드민 판정은 컨트롤러가 `Authentication`의 authority에 `ROLE_ADMIN` 포함 여부로 계산해 서비스에 전달한다(FEED/PERFORMANCE와 동일 패턴).
* 어드민 전용 경로(`/api/admin/**`)는 두지 않는다. 모더레이션 삭제는 동일 엔드포인트에서 principal의 역할로 판정한다.

### 3.2 지원(Application)

- 상태: `PENDING` → `ACCEPTED`/`REJECTED`(공고 작성자) / `WITHDRAWN`(지원자, PENDING에서만).
- **활성 지원(PENDING/ACCEPTED) 유일**: 같은 공고에 활성 지원이 이미 있으면 재지원 불가(`ALREADY_APPLIED`, 409). `REJECTED`/`WITHDRAWN` 뒤엔 재지원(새 레코드) 허용(VERIFIED-PERFORMER의 활성신청 유일 판정 패턴 재사용).
  - 이 유일성은 서비스 계층의 존재 확인 후 저장(check-then-save)으로 판정하는 **best-effort**다. 상태 조건부(PENDING/ACCEPTED) 유니크라 부분 유니크 인덱스가 까다로워 DB 제약은 두지 않는다(VERIFIED-PERFORMER 선례와 동일). 극단적 동시 지원 시 중복 활성 지원이 드물게 생길 수 있으나 기능상 무해하다.
- 공고가 CLOSED/마감돼도 **기존 PENDING 지원은 유지**되고 작성자가 계속 수락/거절 가능. **신규 지원만** 차단.

| 행위 | 자격 | 조건 | 위반 시 |
|---|---|---|---|
| 지원 | 인증 회원(본인 공고 제외) | 공고 지원가능, 활성 지원 없음 | 자기지원 `CANNOT_APPLY_OWN_RECRUITMENT` / 마감 `RECRUITMENT_CLOSED` / 중복 `ALREADY_APPLIED` (각 409) |
| 수락·거절 | 공고 작성자 | 대상 PENDING | `FORBIDDEN`(403-01) / `RECRUITMENT_INVALID_APPLICATION_STATE`(409-10) |
| 철회 | 지원자 본인 | PENDING에서만 | `FORBIDDEN`(403-01) / `RECRUITMENT_INVALID_APPLICATION_STATE`(409-10) |
| 지원자 목록 조회 | 공고 작성자만 | — | `FORBIDDEN`(403-01) |
| 내 지원 목록 조회 | 지원자 본인 | — | — |

* 지원 가능 조건 = `status == OPEN` **그리고** (`deadline == null` 또는 `now < deadline`), 즉 `!posting.isClosed(now)`.

---

## 4. soft delete 규칙 (확정)

* 공고의 모든 조회(목록·단건)는 `deletedAt IS NULL` 조건을 포함한다.
* 삭제된 공고의 단건 조회·수정·마감·지원 시도는 `RECRUITMENT_NOT_FOUND`.
* 지원(Application)은 soft delete를 두지 않는다. 철회는 삭제가 아니라 상태(`WITHDRAWN`)다.

---

## 5. 표시 협력 (확정)

* 공고는 `authorId`, 지원은 `applicantId`만 저장한다. 표시정보(닉네임·인증뱃지)는 **MEMBER 서비스 배치 조회**로 파생한다.
  * FEED/PERFORMANCE에서 쓰는 `MemberQueryService.findDisplaysByIds(Set<Long>) → Map<Long, MemberDisplay>`를 재사용한다(`MemberDisplay{memberId, nickname, verified}`).
  * 목록 응답은 페이지의 `authorId`/`applicantId` 묶음을 한 번에 조회해 N+1을 피한다.
* fetch join을 쓰지 않는 이유: 도메인 경계상 `RecruitmentPosting`/`RecruitmentApplication`에 `Member` 연관 매핑이 없다(작성자·지원자는 원시 Long). 표시정보는 서비스 협력 + IN 배치 조회로 조립한다.
* 공고의 `instruments`(`@ElementCollection`, 다중 악기)는 목록 매핑 시 행마다 지연 로딩되면 N+1이 되므로 `@BatchSize(size = 100)`로 배치 초기화한다(페이지 한 번당 IN 한 번). 전역 `default_batch_fetch_size`는 두지 않고 이 컬렉션에만 스코프한다.

---

## 6. 페이징 & 목록 규칙 (확정)

* 목록·지원자 목록·내 지원 목록 모두 Spring `Pageable`(offset, DOMAIN-COMMON-STATUTE §5).
* `size` 기본 20 / 최대 50(초과 clamp), `page<0`이면 0. FEED/PERFORMANCE와 동일.
* 공고 목록은 `scope` 쿼리로 구분한다(기본 `open`), `instrument` 선택 필터(모집 파트에 해당 악기 포함)를 함께 적용한다:
  * `open`: `deletedAt IS NULL AND status=OPEN AND (deadline IS NULL OR deadline > now)`, 최신순.
  * `closed`: `deletedAt IS NULL AND (status=CLOSED OR (deadline IS NOT NULL AND deadline <= now))`, 최신순.
  * `all`: `deletedAt IS NULL`, 최신순.
* `now` 기준 경계는 서비스에서 `LocalDateTime.now()`로 계산한다.

---

## 7. API

모두 인증 필요(principal = memberId). 경로 접두사 `/api/recruitments`. 어드민 전용 경로는 두지 않는다(모더레이션 삭제는 동일 엔드포인트에서 principal 역할로 판정 — FEED/PERFORMANCE 패턴).

### 7.1 공고(Posting)

* `POST /` : 등록(인증 회원 누구나). body `{title, description, instruments, recruitCount, location, fee, deadline}`. → `RecruitmentPostingResponse`.
* `GET /?scope=open|closed|all&instrument=&page=&size=` : 목록(페이징). `scope` 기본 `open`. → `Page<RecruitmentPostingResponse>`.
* `GET /{id}` : 단건 상세. 없음/삭제 시 `RECRUITMENT_NOT_FOUND`. → `RecruitmentPostingResponse`.
* `PUT /{id}` : 수정(작성자만). 등록과 동일 body(전체 교체, PUT 시맨틱). → `RecruitmentPostingResponse`.
* `POST /{id}/close` : 마감(작성자만, status→CLOSED). → `RecruitmentPostingResponse`.
* `DELETE /{id}` : 삭제(작성자 또는 ADMIN). soft delete. → 성공.

### 7.2 지원(Application)

* `POST /{id}/applications` : 공고에 지원. body `{message}`. 본인공고/마감/중복 시 각 409. → `RecruitmentApplicationResponse`.
* `GET /{id}/applications?page=&size=` : 지원자 목록(공고 작성자만, 아니면 403). → `Page<RecruitmentApplicationResponse>`.
* `GET /applications/me?page=&size=` : 내 지원 목록(지원자 본인). → `Page<RecruitmentApplicationResponse>`.
* `POST /applications/{applicationId}/accept` : 수락(공고 작성자). → `RecruitmentApplicationResponse`.
* `POST /applications/{applicationId}/reject` : 거절(공고 작성자). → `RecruitmentApplicationResponse`.
* `POST /applications/{applicationId}/withdraw` : 철회(지원자 본인, PENDING에서만). → `RecruitmentApplicationResponse`.

### 7.3 응답 DTO

* `RecruitmentPostingResponse`: `id, author{id,nickname,verified}, title, description, instruments, recruitCount, location, fee, deadline, status, closed(파생 boolean), createdAt, updatedAt`.
* `RecruitmentApplicationResponse`: `id, postingId, applicant{id,nickname,verified}, message, status, createdAt, updatedAt`.
  * `author`/`applicant`는 `MemberDisplay` 배치 협력 파생(닉네임·인증뱃지).

---

## 8. 에러 코드 (전역 `ErrorCode`에 추가, 도메인 전용 enum 분리는 계속 보류)

| 코드 | resultCode | HTTP | 사유 |
|---|---|---|---|
| `RECRUITMENT_NOT_FOUND` | 404-08 | 404 | 공고 없음/삭제됨 |
| `RECRUITMENT_APPLICATION_NOT_FOUND` | 404-09 | 404 | 지원 내역 없음 |
| `RECRUITMENT_CLOSED` | 409-07 | 409 | 마감/기한 지난 공고에 지원 시도 |
| `ALREADY_APPLIED` | 409-08 | 409 | 활성 지원 이미 존재(중복 지원) |
| `CANNOT_APPLY_OWN_RECRUITMENT` | 409-09 | 409 | 본인 공고에 지원 시도 |
| `RECRUITMENT_INVALID_APPLICATION_STATE` | 409-10 | 409 | 잘못된 상태 전이(이미 처리된 지원 재처리 등) |

* 권한 위반(수정/마감/삭제/수락·거절/철회)은 기존 `FORBIDDEN`(403-01) 재사용.
* 검증 실패(빈 instruments, message 누락 등)는 기존 `INVALID_INPUT_VALUE`(400-01, Bean Validation) 매핑.
* `APPLICATION_NOT_FOUND`(404-04)/`INVALID_APPLICATION_STATE`(409-06)는 VERIFIED-PERFORMER 도메인에서 이미 사용 중이므로, 이 도메인은 이름이 겹치지 않도록 `RECRUITMENT_` 접두사를 붙인 `RECRUITMENT_APPLICATION_NOT_FOUND`(404-09)/`RECRUITMENT_INVALID_APPLICATION_STATE`(409-10)를 쓴다.

---

## 9. 테스트 (구현 시)

* 엔티티: `RecruitmentPosting` 생성/전체 교체(edit)/마감(close)/soft delete 마킹/`isClosed` 파생(수동 CLOSED, deadline 경과, 상시 모집). `RecruitmentApplication` 생성(PENDING)/accept·reject·withdraw(PENDING에서만 전이, 재처리 시 `RECRUITMENT_INVALID_APPLICATION_STATE`).
* 리포지토리: 공고 `scope`별 목록(open/closed/all) + `instrument` 필터(`member of`) + soft delete 필터, 지원 활성 지원 존재 판정 + 공고별/지원자별 페이징 목록.
* 서비스: 등록(인증 회원 누구나 허용), 수정/마감(작성자 아니면 403), 삭제(작성자·어드민 허용, 타인 403, 대상 접근 404), 지원(본인공고 409/마감 409/중복 409 게이팅), 지원자 목록(작성자만, 아니면 403), 내 지원 목록, 수락·거절(작성자, PENDING 아니면 409), 철회(지원자 본인, PENDING 아니면 409/타인 403), 거절·철회 후 재지원 허용, 작성자·지원자 표시 협력 파생(닉네임·verified).
* 컨트롤러: 인증 회원 등록/목록/상세/수정/마감/삭제 흐름, 악기 필터, 권한 403/404, 페이징·scope 응답, 지원 흐름(지원/지원자목록/내지원목록/수락/거절/철회) 각 성공·실패 케이스.
