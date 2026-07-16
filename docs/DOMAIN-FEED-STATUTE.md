# DOMAIN-FEED-STATUTE

피드 도메인 구현 규칙.

> 작성일: 2026-07-17. 세부 필드·엔드포인트 시그니처는 구현 착수 시 확정하며, 변경 시 이 문서를 갱신한다.
> soft delete·좋아요 멱등·도메인 경계·커서 페이징은 확정 규칙이다.

---

## 1. 패키지

```
com.back.domain.feed
├── controller
├── service
├── repository
├── entity
└── dto
```

---

## 2. 엔티티 (초안)

모두 `BaseEntity` 상속(createdAt/updatedAt). 기존 패턴 준수: `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static 팩토리, 단순 접근자는 Lombok `@Getter`, setter 금지(의도가 드러나는 변경 메서드).

### Post

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `authorId` | Long | 작성 회원. non-null |
| `content` | String | 본문. non-null, ≤2000자 |
| `deletedAt` | LocalDateTime | soft delete 마킹. null=미삭제 |

* 팩토리 `create(authorId, content)`. 변경 메서드 `edit(content)`, `delete()`(=`deletedAt=now`).
* `isDeleted()` 편의 메서드.

### Comment

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `postId` | Long | 소속 게시글. non-null |
| `authorId` | Long | 작성 회원. non-null |
| `content` | String | 본문. non-null, ≤500자 |
| `deletedAt` | LocalDateTime | soft delete 마킹 |

* 평면 구조(대댓글 없음). 팩토리 `create(postId, authorId, content)`. 변경 메서드 `delete()`.
* `postId`는 원시 Long. 게시글 존재·삭제 여부는 서비스에서 확인한다.

### PostLike / CommentLike

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `memberId` | Long | 좋아요 누른 회원. non-null |
| `postId` / `commentId` | Long | 대상. non-null |

* 두 테이블로 분리(FK 무결성·카운트 단순·타입 안전). 각각 **유니크 제약** `unique(memberId, postId)` / `unique(memberId, commentId)`.
* 물리 삭제(좋아요 취소는 레코드 삭제). soft delete 아님.

---

## 3. soft delete 규칙 (확정)

* 게시글·댓글의 모든 조회는 `deletedAt IS NULL` 조건을 포함한다.
* 게시글 soft delete 시 댓글·좋아요는 **cascade 마킹하지 않는다.** 게시글이 조회에서 빠지면 그 댓글목록·단건 조회도 `POST_NOT_FOUND`로 접근 불가해지므로 별도 정리 없이 일관된다. 물리 정리(GC)가 필요하면 추후 배치.
* 삭제된 게시글/댓글에 대한 좋아요·댓글 추가는 대상 없음으로 처리(`POST_NOT_FOUND`/`COMMENT_NOT_FOUND`).

---

## 4. 좋아요 멱등 규칙 (확정)

* 좋아요: 이미 있으면 no-op(추가 안 함), 없으면 1건 생성. 유니크 제약이 최종 방어선.
* 취소: 있으면 삭제, 없으면 no-op.
* 응답은 성공(200)으로 통일한다. 멱등이므로 상태 충돌을 오류로 만들지 않는다.

---

## 5. 작성자 표시 협력 & N+1 방지 (확정)

* FEED는 `authorId`만 저장한다. 작성자 표시정보는 **MEMBER 서비스의 배치 조회**로 파생한다.
  * MEMBER에 배치 조회를 추가한다: `MemberQueryService.findDisplaysByIds(Set<Long> ids) → Map<Long, MemberDisplay>` (`MemberDisplay{memberId, nickname, verified}`).
  * `verified`는 VERIFIED-PERFORMER의 `isVerified`를 MEMBER 협력으로 묶어 채운다(피드 목록에서도 뱃지 노출).
* **fetch join을 쓰지 않는 이유(명시)**:
  1. 도메인 경계상 `Post`에 `Member` 연관 매핑이 없다(작성자는 원시 Long). fetch join 대상 자체가 존재하지 않는다.
  2. 좋아요 수·댓글 수는 연관이 아니라 집계다. fetch join이 아니라 `GROUP BY ... COUNT`로 센다(컬렉션 fetch join은 카테시안 곱·`MultipleBagFetchException` 유발).
* **배치 카운트/내좋아요**: 타임라인 한 페이지의 post id 묶음에 대해
  * 좋아요 수: `PostLike`에서 `postId IN (...) GROUP BY postId`로 1쿼리.
  * 댓글 수: `Comment`에서 `deletedAt IS NULL AND postId IN (...) GROUP BY postId`로 1쿼리.
  * 내 좋아요 여부: `PostLike`에서 `memberId=현재 AND postId IN (...)`로 1쿼리 → id 집합.
  * 결과적으로 페이지당 쿼리 수가 고정(게시글 1 + 작성자맵 1 + 좋아요수 1 + 댓글수 1 + 내좋아요 1)된다.

---

## 6. 커서 페이징 규칙 (확정)

* 타임라인: id 기준 keyset. `WHERE deletedAt IS NULL AND (cursor==null OR id < cursor) ORDER BY id DESC LIMIT size+1`.
  * `size+1`째 존재 여부로 `nextCursor`(마지막 항목 id) 계산. 없으면 `nextCursor=null`(끝).
  * 기본 `size`는 STATUTE 구현 시 확정(예: 20), 상한 둔다.
* 댓글 목록: id 기준 keyset, **오래된순**. `WHERE deletedAt IS NULL AND postId=? AND (cursor==null OR id > cursor) ORDER BY id ASC LIMIT size+1`.

---

## 7. API (초안)

모두 인증 필요(principal = memberId). 경로 접두사 `/api/feed`.

### 7.1 게시글

* `POST /posts` : 작성. body `{content}`. → `PostResponse`.
* `GET /posts?cursor=&size=` : 타임라인(커서, 최신순). → `{items:[PostResponse], nextCursor}`.
* `GET /posts/{id}` : 단건. 삭제/없음이면 `POST_NOT_FOUND`. → `PostResponse`.
* `PUT /posts/{id}` : 수정(작성자만, 아니면 403). body `{content}`. → `PostResponse`.
* `DELETE /posts/{id}` : 삭제(작성자 또는 ADMIN). soft delete. → 성공.

### 7.2 댓글

* `POST /posts/{postId}/comments` : 작성. body `{content}`. 대상 게시글 없으면 `POST_NOT_FOUND`. → `CommentResponse`.
* `GET /posts/{postId}/comments?cursor=&size=` : 댓글 목록(커서, 오래된순). → `{items:[CommentResponse], nextCursor}`.
* `DELETE /comments/{id}` : 삭제(작성자 또는 ADMIN). soft delete. → 성공.

### 7.3 좋아요 (멱등)

* `POST /posts/{id}/like` / `DELETE /posts/{id}/like`.
* `POST /comments/{id}/like` / `DELETE /comments/{id}/like`.
* → 성공(멱등). 응답 본문은 구현 시 확정(빈 성공 또는 갱신된 likeCount).

### 7.4 응답 DTO

* `PostResponse`: `id, author{id, nickname, verified}, content, likeCount, commentCount, likedByMe, createdAt, updatedAt`.
* `CommentResponse`: `id, postId, author{id, nickname, verified}, content, likeCount, likedByMe, createdAt`.
* 커서 목록 래퍼: `{items, nextCursor}` (nextCursor=null이면 끝).

---

## 8. 권한 규칙 (확정)

* 게시글 수정: 작성자만. 위반 시 `FORBIDDEN`(403-01).
* 게시글/댓글 삭제: 작성자 또는 `ROLE_ADMIN`. 위반 시 `FORBIDDEN`(403-01).
* 작성·조회·좋아요: 인증된 회원 누구나.
* 어드민 전용 경로(`/api/admin/**`)는 두지 않는다. 모더레이션 삭제는 동일 엔드포인트에서 principal의 역할로 판정한다.

---

## 9. 에러 코드 (전역 `ErrorCode`에 추가, 도메인 전용 enum 분리는 계속 보류)

| 코드 | resultCode | HTTP | 사유 |
|---|---|---|---|
| `POST_NOT_FOUND` | 404-05 | 404 | 게시글 없음/삭제됨 |
| `COMMENT_NOT_FOUND` | 404-06 | 404 | 댓글 없음/삭제됨 |

* 권한 위반은 기존 `FORBIDDEN`(403-01) 재사용. 번호는 현재 미사용값(404-05, 404-06)으로 확정, 구현 시 최종 대조.

---

## 10. 테스트 (구현 시)

* 엔티티: soft delete 마킹, content 길이 규칙, 좋아요 유니크.
* 리포지토리: 커서 페이징(최신순/오래된순, nextCursor 계산), 배치 카운트(group by IN), 내 좋아요 집합, `deletedAt` 필터.
* 서비스: 작성/수정(작성자 아님 403)/삭제(작성자·어드민 허용, 타인 403), 좋아요 멱등(중복 추가·중복 취소 no-op), 작성자 표시 협력 파생(닉네임·verified), 삭제 대상 접근 404.
* 컨트롤러: 회원 API 흐름, 권한 403/404, 커서 응답 형태.

---

## 11. 구현 착수 시 확정한 결정 (2026-07-17 BE 구현)

* 커서 `size`: **기본 20, 최대 50**(초과 clamp, `<1`이면 기본). 컨트롤러에서 clamp.
* 좋아요 응답: **빈 성공**(`ApiResponse.success()`). 카운트는 목록/단건 조회에서 제공.
* 배치 조회 위치: MEMBER에 **`MemberQueryService` 신설**, `MemberDisplay`는 `com.back.domain.member.dto`. 인증뱃지 N+1 방지는 `VerifiedPerformerService.findVerifiedMemberIds(Set)` 배치.
* `content` 정규화: 별도 트림 없이 `@NotBlank`로 공백-only만 차단(게시글 `@Size(max=2000)`, 댓글 `@Size(max=500)`).
* 게시글 단건 조회는 댓글을 포함하지 않음(댓글은 별도 커서 엔드포인트).
* 좋아요 동시성: 유니크 제약을 최종 방어선으로, `saveAndFlush` + `DataIntegrityViolationException` catch로 이중요청에도 멱등 200 유지(§4).
* 좋아요 유니크 제약의 `@UniqueConstraint(columnNames=...)`는 **물리 컬럼명(snake_case)** 을 쓴다: `{"member_id","post_id"}` / `{"member_id","comment_id"}` (JPA 필드명 camelCase가 아님 — Hibernate 기본 네이밍 전략 기준).
