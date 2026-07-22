# RECRUITMENT(구인) 도메인 설계

> 작성일: 2026-07-22. 브레인스토밍으로 확정한 설계(spec). 정본 도메인 문서(CONSTITUTION/STATUTE)와 구현 계획은 이 문서를 기준으로 파생한다.

---

## 1. 개요 & 방향

음악인이 **연주자를 구하는 구인(求人) 공고를 등록·홍보**하고, 다른 회원이 **지원(application)** 하는 것을 관리하는 도메인.

확정한 방향:

- **구인만** 다룬다(구직 방향은 범위 밖 — MEMBER 프로필/VERIFIED-PERFORMER가 이미 상당 부분 커버, 실수요 생기면 확장).
- 공고(Posting)와 지원(Application) **두 엔티티**로 구성하며 각각 상태를 가진다.
- PERFORMANCE와 **독립**(연결 없음). 구인은 대개 공연 확정 전에 이뤄지므로 nullable FK도 두지 않는다. 실수요 생기면 `performanceId` 추가로 확장.

---

## 2. 책임 & 원칙

- 구인 공고의 생성·수정·마감·삭제 및 조회(목록·단건)를 제공한다.
- 공고에 대한 지원의 생성·상태전이(수락/거절/철회) 및 조회(공고별 지원자 목록·내 지원 목록)를 제공한다.
- 작성자·지원자는 **원시 `Long` ID로만 참조**한다(MEMBER 엔티티 직접 참조 금지 — ARCHITECTURE-CONSTITUTION §4).
  - 응답에 필요한 표시정보(닉네임·인증 뱃지)는 읽기 시점에 `MemberQueryService.findDisplaysByIds(Set<Long>) → Map<Long, MemberDisplay>` 배치 협력으로 파생한다. 닉네임을 복제·소유하지 않는다. N+1 없음(FEED/PERFORMANCE 패턴 재사용).
- **등록 자격은 인증 회원 누구나.** 구인 주체는 반드시 연주자가 아닐 수 있으므로(매니저/기획자/음악감독) 인증 연주자 게이팅을 두지 않는다. PERFORMANCE와 다른 점.
- 공고 삭제는 **soft delete**(`deletedAt`). 지원은 soft delete 없이 상태(WITHDRAWN)로 처리.
- 목록은 페이징 기본(DOMAIN-COMMON, Spring `Pageable`).

---

## 3. 관계

- **MEMBER**: 공고 작성자·지원자는 회원이다. 표시정보(닉네임·인증뱃지)는 MEMBER가 소유하며 서비스 계층으로 협력해 조회만 한다. 어드민은 MEMBER의 `ROLE_ADMIN`(모더레이션 삭제).
- **VERIFIED-PERFORMER**: 등록 게이팅에는 쓰지 않는다. 다만 응답의 인증 뱃지는 `MemberDisplay.verified`(MEMBER가 VERIFIED-PERFORMER와 협력해 채우는 값)를 통해 파생된다.
- **PERFORMANCE**: 현재 연결 없음(독립).

---

## 4. 엔티티

`BaseEntity` 상속(createdAt/updatedAt). 기존 패턴: `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static 팩토리, 단순 접근자 Lombok `@Getter`, setter 금지(의도가 드러나는 변경 메서드).

### 4.1 RecruitmentPosting (soft delete)

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
| `status` | enum(OPEN/CLOSED) | 기본 OPEN |
| `deletedAt` | LocalDateTime | soft delete. null=미삭제 |

- `Instrument`는 MEMBER 프로필의 enum 21종을 **재사용**(어휘 통일, 악기별 필터/매칭).
- 팩토리: `create(authorId, title, description, instruments, recruitCount, location, fee, deadline)`.
- 변경 메서드: `edit(...)`(본문 전체 교체, PUT 시맨틱), `close()`(status→CLOSED), `delete()`(deletedAt=now), `isDeleted()`.
- 파생 판정(엔티티 또는 서비스): `isClosed(now)` = `status == CLOSED || (deadline != null && now >= deadline)`.

### 4.2 RecruitmentApplication (soft delete 없음 — 철회는 상태)

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `postingId` | Long | 대상 공고. non-null |
| `applicantId` | Long | 지원자. non-null |
| `message` | String | 지원 메시지(어느 파트 지원인지 등 자유 기술). ≤1000, non-null |
| `status` | enum(PENDING/ACCEPTED/REJECTED/WITHDRAWN) | 기본 PENDING |

- 다중 악기 공고에서 "어느 파트에 지원하는지"는 `message` 텍스트로 받는다(분기 처리).
- 변경 메서드: `accept()`, `reject()`, `withdraw()` — 각각 PENDING에서만 전이(아니면 상태오류).

---

## 5. 상태머신 & 권한

### 5.1 공고(Posting)

- 상태: `OPEN` → `CLOSED`(작성자 수동 마감).
- 마감 판정은 파생: `status == CLOSED` **또는** (`deadline != null` 그리고 `now >= deadline`). 상태 필드 자체는 수동 전환만(자동으로 CLOSED로 바꾸지 않음).
- **지원 가능 조건** = `status == OPEN` **그리고** (`deadline == null` 또는 `now < deadline`).

| 행위 | 자격 | 위반 시 |
|---|---|---|
| 등록 | 인증 회원 누구나 | — |
| 수정 | 작성자 본인 | 403-01 |
| 마감(CLOSED) | 작성자 본인 | 403-01 |
| 삭제(soft) | 작성자 또는 ROLE_ADMIN | 403-01 |
| 조회·목록 | 인증 회원 누구나 | — |

### 5.2 지원(Application)

- 상태: `PENDING` → `ACCEPTED`/`REJECTED`(공고 작성자) / `WITHDRAWN`(지원자, PENDING에서만).
- **활성 지원(PENDING/ACCEPTED) 유일**: 같은 공고에 활성 지원이 이미 있으면 재지원 불가(409). REJECTED/WITHDRAWN 뒤엔 재지원(새 레코드) 허용. VERIFIED-PERFORMER 패턴 재사용.
- 공고가 CLOSED/마감돼도 **기존 PENDING 지원은 유지**되고 작성자가 계속 수락/거절 가능. **신규 지원만** 차단.

| 행위 | 자격 | 조건 | 위반 시 |
|---|---|---|---|
| 지원 | 인증 회원(본인 공고 제외) | 공고 지원가능, 활성 지원 없음 | 자기지원/마감/중복 각 409 |
| 수락·거절 | 공고 작성자 | 대상 PENDING | 403-01 / 상태오류 409 |
| 철회 | 지원자 본인 | PENDING에서만 | 403-01 / 상태오류 409 |
| 지원자 목록 조회 | 공고 작성자만 | — | 403-01 |
| 내 지원 목록 조회 | 지원자 본인 | — | — |

---

## 6. API

모두 인증 필요(principal = memberId). 경로 접두사 `/api/recruitments`. 어드민 전용 경로는 두지 않는다(모더레이션 삭제는 동일 엔드포인트에서 principal 역할로 판정 — FEED/PERFORMANCE 패턴).

### 6.1 공고(Posting)

- `POST /` : 등록. body `{title, description, instruments, recruitCount, location, fee, deadline}`. → `RecruitmentResponse`
- `GET /?scope=open|closed|all&instrument=&page=&size=` : 목록(페이징). `scope` 기본 `open`. `instrument` 선택 필터(모집 파트에 해당 악기 포함). → `Page<RecruitmentResponse>`
  - `open`: `deletedAt IS NULL AND status=OPEN AND (deadline IS NULL OR deadline > now)`, 최신순.
  - `closed`: `deletedAt IS NULL AND (status=CLOSED OR (deadline IS NOT NULL AND deadline <= now))`, 최신순.
  - `all`: `deletedAt IS NULL`, 최신순.
- `GET /{id}` : 단건 상세. 없음/삭제 시 `RECRUITMENT_NOT_FOUND`. → `RecruitmentResponse`
- `PUT /{id}` : 수정(작성자). 등록과 동일 body(전체 교체, PUT 시맨틱). → `RecruitmentResponse`
- `POST /{id}/close` : 마감(작성자, status→CLOSED). → `RecruitmentResponse`
- `DELETE /{id}` : 삭제(작성자 또는 ADMIN). soft delete. → 성공

### 6.2 지원(Application)

- `POST /{id}/applications` : 공고에 지원. body `{message}`. 본인공고/마감/중복 시 각 409. → `ApplicationResponse`
- `GET /{id}/applications?page=&size=` : 지원자 목록(공고 작성자만, 아니면 403). → `Page<ApplicationResponse>`
- `GET /applications/me?page=&size=` : 내 지원 목록(지원자 본인). → `Page<ApplicationResponse>`
- `POST /applications/{applicationId}/accept` : 수락(공고 작성자). → `ApplicationResponse`
- `POST /applications/{applicationId}/reject` : 거절(공고 작성자). → `ApplicationResponse`
- `POST /applications/{applicationId}/withdraw` : 철회(지원자 본인, PENDING에서만). → `ApplicationResponse`

### 6.3 응답 DTO

- `RecruitmentResponse`: `id, author{id,nickname,verified}, title, description, instruments, recruitCount, location, fee, deadline, status, closed(파생 boolean), applicationCount(선택), createdAt, updatedAt`
- `ApplicationResponse`: `id, postingId, applicant{id,nickname,verified}, message, status, createdAt, updatedAt`
  - `author`/`applicant`는 `MemberDisplay` 배치 협력 파생(닉네임·인증뱃지).

---

## 7. 에러코드 (전역 `ErrorCode` 추가, 번호는 구현 시 전역 enum 대조 후 최종 확정)

| 코드 | resultCode(초안) | HTTP | 사유 |
|---|---|---|---|
| `RECRUITMENT_NOT_FOUND` | 404-08 | 404 | 공고 없음/삭제됨 |
| `APPLICATION_NOT_FOUND` | 404-09 | 404 | 지원 건 없음(RECRUITMENT 도메인, VERIFIED-PERFORMER 404-04와 별개) |
| `RECRUITMENT_CLOSED` | 409-07 | 409 | 마감/기한 지난 공고에 지원 시도 |
| `ALREADY_APPLIED` | 409-08 | 409 | 활성 지원 이미 존재(중복 지원) |
| `CANNOT_APPLY_OWN_RECRUITMENT` | 409-09 | 409 | 본인 공고에 지원 시도 |
| `INVALID_APPLICATION_STATE` | 409-10 | 409 | 잘못된 상태 전이(이미 처리된 지원 수락 등) |

- 권한 위반(수정/마감/삭제/수락·거절/철회)은 기존 `FORBIDDEN`(403-01) 재사용.
- 검증 실패(빈 instruments, message 누락 등)는 기존 400-01(Bean Validation) 매핑.

---

## 8. 페이징 규칙

- 목록·지원자 목록·내 지원 목록 모두 Spring `Pageable`(offset, DOMAIN-COMMON-STATUTE §5).
- `size` 기본 20 / 최대 50(초과 clamp), `page<0`이면 0. FEED/PERFORMANCE와 동일.
- 목록 응답은 페이지의 `authorId`/`applicantId` 묶음을 한 번에 조회해 N+1 회피.

---

## 9. 동시성 & 멱등

- 활성 지원 유일: `(postingId, applicantId, status IN (PENDING,ACCEPTED))`에 대한 판정. DB 유니크 제약을 부분 인덱스로 걸기 어려우므로(상태 조건부), 서비스에서 활성 지원 존재 확인 + 저장. 경합 시 재확인. (VERIFIED-PERFORMER의 활성신청 유일 판정 패턴 재사용, 구현 시 정확한 동시성 전략 확정.)
- 상태 전이(accept/reject/withdraw)는 PENDING 확인 후 전이. 이중 처리 시 `INVALID_APPLICATION_STATE`.

---

## 10. 범위 밖 (현재)

- 구직(연주자가 자리 찾는) 공고 — MEMBER 프로필/VERIFIED-PERFORMER로 커버, 실수요 시 확장.
- PERFORMANCE 연결(`performanceId`) — 실수요 시 nullable FK 추가.
- 지원 첨부파일(포트폴리오/음원) — 필요 시 FileService 협력으로 확장.
- 지원자↔작성자 메시징/채팅 — CHAT 도메인 도입 시 연계.
- 공고 검색(제목/지역 키워드, 태그) — 현재는 scope + instrument 필터만.
- 알림(지원 접수/수락 푸시) — 알림 인프라 도입 시.
- 마감 자동화 배치(deadline 지난 공고 status 일괄 CLOSED 전환) — 현재는 조회/지원 시 파생 판정으로 처리, 상태 필드는 수동 전환만.
