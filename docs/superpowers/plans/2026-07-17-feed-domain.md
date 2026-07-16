# FEED 도메인 BE 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 자유 게시글·평면 댓글·좋아요(게시글/댓글)·전역 커서 타임라인을 제공하는 FEED 도메인 BE를 TDD로 구현한다.

**Architecture:** `com.back.domain.feed` 패키지에 엔티티 4종(Post/Comment/PostLike/CommentLike)·리포지토리·서비스 3종(게시글/댓글/좋아요)·컨트롤러 3종을 둔다. 작성자 표시(닉네임·인증뱃지)는 도메인 경계상 `authorId`(Long)만 저장하고 MEMBER 서비스 배치 조회로 파생한다. soft delete·좋아요 멱등·id keyset 커서 페이징을 규칙으로 한다.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / Spring Data JPA / JUnit5 + AssertJ + MockMvc / H2(test).

## Global Constraints

- Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21. 래퍼·부트 버전 올리지 말 것.
- 모든 엔티티는 `com.back.global.common.BaseEntity` 상속(createdAt/updatedAt). `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static 팩토리, 단순 접근자는 Lombok `@Getter`, setter 금지.
- 도메인 간 참조는 원시 id(Long)만. 다른 도메인 엔티티 직접 참조 금지(ARCHITECTURE-CONSTITUTION §4). 도메인 내부(FEED)도 Long id로 통일(DOMAIN-FEED-STATUTE §2).
- 응답은 `com.back.global.common.ApiResponse<T>`로 감싼다(`success(data)` / `success()`). 요청/응답은 DTO(record)만. Entity 노출 금지.
- 비즈니스 예외는 `BusinessException(ErrorCode)`. 전역 `GlobalExceptionHandler`가 응답 변환. 새 에러코드는 전역 `ErrorCode` enum에 추가.
- `@SpringBootTest`에는 반드시 `@ActiveProfiles("test")`(H2). 서비스/리포지토리 단위는 `@DataJpaTest`.
- 인증 필요 API의 principal은 `@AuthenticationPrincipal Long memberId`(JwtProvider가 subject=memberId, authority=ROLE_* 세팅).
- 목록은 페이징 기본. 타임라인 기본 size=20, 최대 50.
- 좋아요 토글 응답은 빈 성공(`ApiResponse.success()`). 카운트는 목록/단건 조회에서 제공.
- 커밋 메시지는 한글 `유형: 요약`. 커밋은 각 태스크 끝에서.

---

## 파일 구조

**신규 — `BE/src/main/java/com/back/domain/feed/`**
- `entity/Post.java` — 게시글 엔티티(soft delete)
- `entity/Comment.java` — 평면 댓글 엔티티(soft delete)
- `entity/PostLike.java` — 게시글 좋아요(유니크)
- `entity/CommentLike.java` — 댓글 좋아요(유니크)
- `repository/PostRepository.java` — 단건(active)·타임라인 커서
- `repository/CommentRepository.java` — 단건(active)·댓글 커서·댓글 수 배치
- `repository/PostLikeRepository.java` — 존재/삭제·좋아요 수 배치·내 좋아요 집합
- `repository/CommentLikeRepository.java` — 존재/삭제·좋아요 수 배치·내 좋아요 집합
- `repository/IdCount.java` — 배치 카운트 projection(공용)
- `service/FeedPostService.java` — 게시글 작성/조회/타임라인/수정/삭제
- `service/FeedCommentService.java` — 댓글 작성/목록/삭제
- `service/FeedLikeService.java` — 게시글/댓글 좋아요·취소(멱등)
- `dto/CreatePostRequest.java`, `dto/UpdatePostRequest.java`, `dto/CreateCommentRequest.java`
- `dto/PostResponse.java`, `dto/CommentResponse.java`, `dto/CursorPage.java`
- `controller/FeedPostController.java`, `controller/FeedCommentController.java`, `controller/FeedLikeController.java`

**신규 — MEMBER 협력**
- `domain/member/dto/MemberDisplay.java` — 작성자 표시 DTO(memberId, nickname, verified)
- `domain/member/service/MemberQueryService.java` — `findDisplaysByIds` 배치 조회

**수정**
- `domain/verifiedperformer/repository/VerificationApplicationRepository.java` — 승인 회원 배치 조회 추가
- `domain/verifiedperformer/service/VerifiedPerformerService.java` — `findVerifiedMemberIds` 추가
- `global/exception/ErrorCode.java` — `POST_NOT_FOUND`(404-05), `COMMENT_NOT_FOUND`(404-06) 추가

---

## Task 1: Post 엔티티

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/entity/Post.java`
- Test: `BE/src/test/java/com/back/domain/feed/entity/PostTest.java`

**Interfaces:**
- Produces: `Post.create(Long authorId, String content) -> Post`; `post.edit(String content)`; `post.delete()`; `post.isDeleted() -> boolean`; getters `getId/getAuthorId/getContent/getDeletedAt`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostTest {

    @Test
    void create는_미삭제_게시글을_만든다() {
        Post post = Post.create(1L, "첫 글");

        assertThat(post.getAuthorId()).isEqualTo(1L);
        assertThat(post.getContent()).isEqualTo("첫 글");
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void edit는_본문을_교체한다() {
        Post post = Post.create(1L, "이전");

        post.edit("이후");

        assertThat(post.getContent()).isEqualTo("이후");
    }

    @Test
    void delete는_deletedAt을_마킹한다() {
        Post post = Post.create(1L, "글");

        post.delete();

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.entity.PostTest"`
Expected: FAIL — `Post` 심볼 없음(컴파일 에러).

- [ ] **Step 3: Write minimal implementation**

```java
package com.back.domain.feed.entity;

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

/** 자유 게시글. 작성자는 원시 authorId(Long)로만 참조한다. 삭제는 soft delete. */
@Entity
@Table(name = "feed_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 2000)
    private String content;

    private LocalDateTime deletedAt;

    private Post(Long authorId, String content) {
        this.authorId = authorId;
        this.content = content;
    }

    public static Post create(Long authorId, String content) {
        return new Post(authorId, content);
    }

    public void edit(String content) {
        this.content = content;
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

Run: `./gradlew -p BE test --tests "com.back.domain.feed.entity.PostTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/entity/Post.java BE/src/test/java/com/back/domain/feed/entity/PostTest.java
git commit -m "feat: FEED Post 엔티티(soft delete) 추가"
```

---

## Task 2: Comment 엔티티

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/entity/Comment.java`
- Test: `BE/src/test/java/com/back/domain/feed/entity/CommentTest.java`

**Interfaces:**
- Produces: `Comment.create(Long postId, Long authorId, String content) -> Comment`; `comment.delete()`; `comment.isDeleted() -> boolean`; getters `getId/getPostId/getAuthorId/getContent/getDeletedAt`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommentTest {

    @Test
    void create는_미삭제_댓글을_만든다() {
        Comment comment = Comment.create(10L, 1L, "댓글");

        assertThat(comment.getPostId()).isEqualTo(10L);
        assertThat(comment.getAuthorId()).isEqualTo(1L);
        assertThat(comment.getContent()).isEqualTo("댓글");
        assertThat(comment.isDeleted()).isFalse();
    }

    @Test
    void delete는_deletedAt을_마킹한다() {
        Comment comment = Comment.create(10L, 1L, "댓글");

        comment.delete();

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.entity.CommentTest"`
Expected: FAIL — `Comment` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

```java
package com.back.domain.feed.entity;

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

/** 평면 댓글(대댓글 없음). postId/authorId는 원시 Long. 삭제는 soft delete. */
@Entity
@Table(name = "feed_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 500)
    private String content;

    private LocalDateTime deletedAt;

    private Comment(Long postId, Long authorId, String content) {
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
    }

    public static Comment create(Long postId, Long authorId, String content) {
        return new Comment(postId, authorId, content);
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

Run: `./gradlew -p BE test --tests "com.back.domain.feed.entity.CommentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/entity/Comment.java BE/src/test/java/com/back/domain/feed/entity/CommentTest.java
git commit -m "feat: FEED Comment 엔티티(평면, soft delete) 추가"
```

---

## Task 3: PostLike / CommentLike 엔티티

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/entity/PostLike.java`
- Create: `BE/src/main/java/com/back/domain/feed/entity/CommentLike.java`
- Test: `BE/src/test/java/com/back/domain/feed/entity/LikeEntityTest.java`

**Interfaces:**
- Produces: `PostLike.create(Long memberId, Long postId)`; `CommentLike.create(Long memberId, Long commentId)`; 각 getter.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikeEntityTest {

    @Test
    void PostLike_create는_회원과_게시글을_가진다() {
        PostLike like = PostLike.create(1L, 10L);

        assertThat(like.getMemberId()).isEqualTo(1L);
        assertThat(like.getPostId()).isEqualTo(10L);
    }

    @Test
    void CommentLike_create는_회원과_댓글을_가진다() {
        CommentLike like = CommentLike.create(1L, 20L);

        assertThat(like.getMemberId()).isEqualTo(1L);
        assertThat(like.getCommentId()).isEqualTo(20L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.entity.LikeEntityTest"`
Expected: FAIL — `PostLike`/`CommentLike` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`PostLike.java`:
```java
package com.back.domain.feed.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 좋아요. 회원당 게시글당 최대 1(유니크). 취소는 물리 삭제. */
@Entity
@Table(name = "feed_post_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"memberId", "postId"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long postId;

    private PostLike(Long memberId, Long postId) {
        this.memberId = memberId;
        this.postId = postId;
    }

    public static PostLike create(Long memberId, Long postId) {
        return new PostLike(memberId, postId);
    }
}
```

`CommentLike.java`:
```java
package com.back.domain.feed.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 댓글 좋아요. 회원당 댓글당 최대 1(유니크). 취소는 물리 삭제. */
@Entity
@Table(name = "feed_comment_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"memberId", "commentId"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long commentId;

    private CommentLike(Long memberId, Long commentId) {
        this.memberId = memberId;
        this.commentId = commentId;
    }

    public static CommentLike create(Long memberId, Long commentId) {
        return new CommentLike(memberId, commentId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.entity.LikeEntityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/entity/PostLike.java BE/src/main/java/com/back/domain/feed/entity/CommentLike.java BE/src/test/java/com/back/domain/feed/entity/LikeEntityTest.java
git commit -m "feat: FEED PostLike/CommentLike 엔티티(유니크) 추가"
```

---

## Task 4: IdCount projection + 리포지토리 4종

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/repository/IdCount.java`
- Create: `BE/src/main/java/com/back/domain/feed/repository/PostRepository.java`
- Create: `BE/src/main/java/com/back/domain/feed/repository/CommentRepository.java`
- Create: `BE/src/main/java/com/back/domain/feed/repository/PostLikeRepository.java`
- Create: `BE/src/main/java/com/back/domain/feed/repository/CommentLikeRepository.java`
- Test: `BE/src/test/java/com/back/domain/feed/repository/FeedRepositoryTest.java`

**Interfaces:**
- Consumes: `Post`, `Comment`, `PostLike`, `CommentLike` (Tasks 1–3).
- Produces:
  - `IdCount { Long getId(); long getCount(); }`
  - `PostRepository`: `Optional<Post> findByIdAndDeletedAtIsNull(Long)`; `List<Post> findTimeline(Long cursor, Pageable)`.
  - `CommentRepository`: `Optional<Comment> findByIdAndDeletedAtIsNull(Long)`; `List<Comment> findCommentPage(Long postId, Long cursor, Pageable)`; `long countByPostIdAndDeletedAtIsNull(Long)`; `List<IdCount> countByPostIds(Collection<Long> ids)`.
  - `PostLikeRepository`: `boolean existsByMemberIdAndPostId(Long,Long)`; `void deleteByMemberIdAndPostId(Long,Long)`; `long countByPostId(Long)`; `List<IdCount> countByPostIds(Collection<Long> ids)`; `List<Long> findLikedPostIds(Long memberId, Collection<Long> ids)`.
  - `CommentLikeRepository`: `boolean existsByMemberIdAndCommentId(Long,Long)`; `void deleteByMemberIdAndCommentId(Long,Long)`; `long countByCommentId(Long)`; `List<IdCount> countByCommentIds(Collection<Long> ids)`; `List<Long> findLikedCommentIds(Long memberId, Collection<Long> ids)`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.feed.entity.Comment;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.entity.PostLike;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class FeedRepositoryTest {

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PostLikeRepository postLikeRepository;

    @Test
    void 타임라인은_미삭제글을_id_내림차순_커서로_가져온다() {
        Post p1 = postRepository.save(Post.create(1L, "1"));
        Post p2 = postRepository.save(Post.create(1L, "2"));
        Post p3 = postRepository.save(Post.create(1L, "3"));
        p2.delete();
        postRepository.saveAndFlush(p2);

        List<Post> firstPage = postRepository.findTimeline(null, PageRequest.of(0, 10));
        assertThat(firstPage).extracting(Post::getId).containsExactly(p3.getId(), p1.getId());

        List<Post> afterCursor = postRepository.findTimeline(p3.getId(), PageRequest.of(0, 10));
        assertThat(afterCursor).extracting(Post::getId).containsExactly(p1.getId());
    }

    @Test
    void 미삭제_단건만_조회된다() {
        Post post = postRepository.save(Post.create(1L, "글"));
        assertThat(postRepository.findByIdAndDeletedAtIsNull(post.getId())).isPresent();

        post.delete();
        postRepository.saveAndFlush(post);
        assertThat(postRepository.findByIdAndDeletedAtIsNull(post.getId())).isEmpty();
    }

    @Test
    void 댓글은_미삭제만_오래된순_커서로_가져온다() {
        Comment c1 = commentRepository.save(Comment.create(10L, 1L, "a"));
        Comment c2 = commentRepository.save(Comment.create(10L, 1L, "b"));
        Comment other = commentRepository.save(Comment.create(99L, 1L, "다른글"));

        List<Comment> page = commentRepository.findCommentPage(10L, null, PageRequest.of(0, 10));
        assertThat(page).extracting(Comment::getId).containsExactly(c1.getId(), c2.getId());
    }

    @Test
    void 좋아요_수를_게시글별로_배치_집계한다() {
        postLikeRepository.save(PostLike.create(1L, 10L));
        postLikeRepository.save(PostLike.create(2L, 10L));
        postLikeRepository.save(PostLike.create(1L, 11L));

        Map<Long, Long> counts = postLikeRepository.countByPostIds(List.of(10L, 11L)).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCount));

        assertThat(counts).containsEntry(10L, 2L).containsEntry(11L, 1L);
    }

    @Test
    void 내가_좋아요한_게시글_id를_배치로_가져온다() {
        postLikeRepository.save(PostLike.create(1L, 10L));
        postLikeRepository.save(PostLike.create(2L, 11L));

        List<Long> liked = postLikeRepository.findLikedPostIds(1L, List.of(10L, 11L));
        assertThat(liked).containsExactly(10L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.repository.FeedRepositoryTest"`
Expected: FAIL — 리포지토리 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`IdCount.java`:
```java
package com.back.domain.feed.repository;

/** 배치 카운트 projection. 대상 id → 개수. */
public interface IdCount {
    Long getId();
    long getCount();
}
```

`PostRepository.java`:
```java
package com.back.domain.feed.repository;

import com.back.domain.feed.entity.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    /** 전역 타임라인: 미삭제 글을 id 내림차순 keyset 으로. cursor=null 이면 최신부터. */
    @Query("select p from Post p "
            + "where p.deletedAt is null and (:cursor is null or p.id < :cursor) "
            + "order by p.id desc")
    List<Post> findTimeline(@Param("cursor") Long cursor, Pageable pageable);
}
```

`CommentRepository.java`:
```java
package com.back.domain.feed.repository;

import com.back.domain.feed.entity.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    /** 특정 게시글의 미삭제 댓글을 id 오름차순 keyset 으로(오래된순). */
    @Query("select c from Comment c "
            + "where c.postId = :postId and c.deletedAt is null "
            + "and (:cursor is null or c.id > :cursor) "
            + "order by c.id asc")
    List<Comment> findCommentPage(@Param("postId") Long postId, @Param("cursor") Long cursor,
            Pageable pageable);

    long countByPostIdAndDeletedAtIsNull(Long postId);

    /** 게시글별 미삭제 댓글 수 배치 집계. */
    @Query("select c.postId as id, count(c) as count from Comment c "
            + "where c.deletedAt is null and c.postId in :ids group by c.postId")
    List<IdCount> countByPostIds(@Param("ids") Collection<Long> ids);
}
```

`PostLikeRepository.java`:
```java
package com.back.domain.feed.repository;

import com.back.domain.feed.entity.PostLike;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByMemberIdAndPostId(Long memberId, Long postId);

    @Modifying
    void deleteByMemberIdAndPostId(Long memberId, Long postId);

    long countByPostId(Long postId);

    @Query("select pl.postId as id, count(pl) as count from PostLike pl "
            + "where pl.postId in :ids group by pl.postId")
    List<IdCount> countByPostIds(@Param("ids") Collection<Long> ids);

    @Query("select pl.postId from PostLike pl "
            + "where pl.memberId = :memberId and pl.postId in :ids")
    List<Long> findLikedPostIds(@Param("memberId") Long memberId,
            @Param("ids") Collection<Long> ids);
}
```

`CommentLikeRepository.java`:
```java
package com.back.domain.feed.repository;

import com.back.domain.feed.entity.CommentLike;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByMemberIdAndCommentId(Long memberId, Long commentId);

    @Modifying
    void deleteByMemberIdAndCommentId(Long memberId, Long commentId);

    long countByCommentId(Long commentId);

    @Query("select cl.commentId as id, count(cl) as count from CommentLike cl "
            + "where cl.commentId in :ids group by cl.commentId")
    List<IdCount> countByCommentIds(@Param("ids") Collection<Long> ids);

    @Query("select cl.commentId from CommentLike cl "
            + "where cl.memberId = :memberId and cl.commentId in :ids")
    List<Long> findLikedCommentIds(@Param("memberId") Long memberId,
            @Param("ids") Collection<Long> ids);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.repository.FeedRepositoryTest"`
Expected: PASS.

> 참고: `deleteBy...` 파생 삭제는 `@DataJpaTest` 트랜잭션 안에서 동작한다. 서비스에서 호출 시 `@Transactional` 필요(Task 7~9에서 클래스 레벨 트랜잭션으로 충족).

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/repository/ BE/src/test/java/com/back/domain/feed/repository/FeedRepositoryTest.java
git commit -m "feat: FEED 리포지토리 4종(커서/배치 카운트/내 좋아요) 추가"
```

---

## Task 5: VERIFIED-PERFORMER 승인 회원 배치 조회

**Files:**
- Modify: `BE/src/main/java/com/back/domain/verifiedperformer/repository/VerificationApplicationRepository.java`
- Modify: `BE/src/main/java/com/back/domain/verifiedperformer/service/VerifiedPerformerService.java`
- Test: `BE/src/test/java/com/back/domain/verifiedperformer/service/VerifiedPerformerServiceTest.java` (기존 파일에 테스트 추가)

**Interfaces:**
- Produces: `VerifiedPerformerService.findVerifiedMemberIds(Set<Long> ids) -> Set<Long>` (APPROVED 레코드가 있는 회원 id만).
- Consumes: 기존 `VerificationApplicationRepository`, `VerificationStatus.APPROVED`.

- [ ] **Step 1: Write the failing test** (기존 `VerifiedPerformerServiceTest`에 추가)

```java
    @Test
    void 승인된_회원_id만_배치로_돌려준다() {
        ApplicationResponse pending = service.apply(1L, applyRequest());
        service.approve(pending.id(), ADMIN, "승인");     // 1L 승인
        service.apply(2L, applyRequest());                // 2L 은 PENDING

        java.util.Set<Long> verified =
                service.findVerifiedMemberIds(java.util.Set.of(1L, 2L, 3L));

        assertThat(verified).containsExactly(1L);
    }

    @Test
    void 빈_입력은_빈_집합을_돌려준다() {
        assertThat(service.findVerifiedMemberIds(java.util.Set.of())).isEmpty();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.verifiedperformer.service.VerifiedPerformerServiceTest"`
Expected: FAIL — `findVerifiedMemberIds` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

Repository에 추가:
```java
    @org.springframework.data.jpa.repository.Query(
            "select distinct a.memberId from VerificationApplication a "
            + "where a.status = com.back.domain.verifiedperformer.entity.VerificationStatus.APPROVED "
            + "and a.memberId in :ids")
    java.util.List<Long> findApprovedMemberIds(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
```

Service에 추가(import: `java.util.Set`, `java.util.Collections`):
```java
    /** 주어진 회원들 중 인증(APPROVED) 상태인 회원 id 집합. 피드 등 목록 뱃지 파생의 N+1 방지용. */
    @Transactional(readOnly = true)
    public Set<Long> findVerifiedMemberIds(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findApprovedMemberIds(memberIds));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.verifiedperformer.service.VerifiedPerformerServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/verifiedperformer/
git add BE/src/test/java/com/back/domain/verifiedperformer/service/VerifiedPerformerServiceTest.java
git commit -m "feat: 인증 회원 배치 조회(findVerifiedMemberIds) 추가"
```

---

## Task 6: MemberDisplay + MemberQueryService(작성자 배치 조회)

**Files:**
- Create: `BE/src/main/java/com/back/domain/member/dto/MemberDisplay.java`
- Create: `BE/src/main/java/com/back/domain/member/service/MemberQueryService.java`
- Test: `BE/src/test/java/com/back/domain/member/service/MemberQueryServiceTest.java`

**Interfaces:**
- Consumes: `MemberRepository.findAllById(Iterable)` (JpaRepository 기본), `VerifiedPerformerService.findVerifiedMemberIds(Set<Long>)` (Task 5).
- Produces: `MemberDisplay(Long memberId, String nickname, boolean verified)`; `MemberQueryService.findDisplaysByIds(Set<Long> ids) -> Map<Long, MemberDisplay>`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class MemberQueryServiceTest {

    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private MemberQueryService service;
    private VerifiedPerformerService verifiedPerformerService;

    @BeforeEach
    void setUp() {
        verifiedPerformerService = new VerifiedPerformerService(verificationApplicationRepository);
        service = new MemberQueryService(memberRepository, verifiedPerformerService);
    }

    @Test
    void 회원_표시정보를_배치로_돌려주고_인증뱃지를_채운다() {
        Member a = memberRepository.save(Member.createLocal("a", "pw", "a@x.com", "가"));
        Member b = memberRepository.save(Member.createLocal("b", "pw", "b@x.com", "나"));
        verifiedPerformerService.grant(new GrantRequest(a.getId(), "지정"), 99L); // a 인증

        Map<Long, MemberDisplay> map = service.findDisplaysByIds(Set.of(a.getId(), b.getId()));

        assertThat(map.get(a.getId()).nickname()).isEqualTo("가");
        assertThat(map.get(a.getId()).verified()).isTrue();
        assertThat(map.get(b.getId()).verified()).isFalse();
    }

    @Test
    void 빈_입력은_빈_맵() {
        assertThat(service.findDisplaysByIds(Set.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.member.service.MemberQueryServiceTest"`
Expected: FAIL — `MemberDisplay`/`MemberQueryService` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`MemberDisplay.java`:
```java
package com.back.domain.member.dto;

/** 다른 도메인(FEED 등)에 노출할 작성자 표시정보. 닉네임과 인증 뱃지. */
public record MemberDisplay(Long memberId, String nickname, boolean verified) {
}
```

`MemberQueryService.java`:
```java
package com.back.domain.member.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 작성자/회원 표시정보를 조회할 때 쓰는 협력 서비스.
 * id 묶음을 한 번에 조회(N+1 방지)하고 인증 뱃지를 함께 파생한다.
 */
@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final MemberRepository memberRepository;
    private final VerifiedPerformerService verifiedPerformerService;

    @Transactional(readOnly = true)
    public Map<Long, MemberDisplay> findDisplaysByIds(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> verifiedIds = verifiedPerformerService.findVerifiedMemberIds(memberIds);
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, member -> new MemberDisplay(
                        member.getId(), member.getNickname(), verifiedIds.contains(member.getId())),
                        (a, b) -> a));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.member.service.MemberQueryServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/member/dto/MemberDisplay.java
git add BE/src/main/java/com/back/domain/member/service/MemberQueryService.java
git add BE/src/test/java/com/back/domain/member/service/MemberQueryServiceTest.java
git commit -m "feat: MEMBER 작성자 표시정보 배치 조회(MemberQueryService) 추가"
```

---

## Task 7: ErrorCode + DTO + FeedPostService

**Files:**
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java`
- Create: `BE/src/main/java/com/back/domain/feed/dto/CreatePostRequest.java`
- Create: `BE/src/main/java/com/back/domain/feed/dto/UpdatePostRequest.java`
- Create: `BE/src/main/java/com/back/domain/feed/dto/PostResponse.java`
- Create: `BE/src/main/java/com/back/domain/feed/dto/CursorPage.java`
- Create: `BE/src/main/java/com/back/domain/feed/service/FeedPostService.java`
- Test: `BE/src/test/java/com/back/domain/feed/service/FeedPostServiceTest.java`

**Interfaces:**
- Consumes: `PostRepository`, `PostLikeRepository`, `CommentRepository` (Task 4), `MemberQueryService` (Task 6), `ErrorCode.POST_NOT_FOUND`, `ErrorCode.FORBIDDEN`.
- Produces:
  - `CreatePostRequest(@NotBlank @Size(max=2000) String content)`
  - `UpdatePostRequest(@NotBlank @Size(max=2000) String content)`
  - `CursorPage<T>(List<T> items, Long nextCursor)`
  - `PostResponse(Long id, MemberDisplay author, String content, long likeCount, long commentCount, boolean likedByMe, LocalDateTime createdAt, LocalDateTime updatedAt)`
  - `FeedPostService`: `createPost(Long authorId, CreatePostRequest) -> PostResponse`; `getPost(Long viewerId, Long postId) -> PostResponse`; `getTimeline(Long viewerId, Long cursor, int size) -> CursorPage<PostResponse>`; `editPost(Long editorId, Long postId, UpdatePostRequest) -> PostResponse`; `deletePost(Long requesterId, boolean requesterIsAdmin, Long postId) -> void`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.feed.dto.CreatePostRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.dto.PostResponse;
import com.back.domain.feed.dto.UpdatePostRequest;
import com.back.domain.feed.entity.PostLike;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostLikeRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FeedPostServiceTest {

    @Autowired PostRepository postRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private FeedPostService service;
    private Long authorId;

    @BeforeEach
    void setUp() {
        VerifiedPerformerService vps = new VerifiedPerformerService(verificationApplicationRepository);
        MemberQueryService memberQueryService = new MemberQueryService(memberRepository, vps);
        service = new FeedPostService(postRepository, postLikeRepository, commentRepository,
                memberQueryService);
        Member author = memberRepository.save(Member.createLocal("w", "pw", "w@x.com", "글쓴이"));
        authorId = author.getId();
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 게시글을_작성하면_작성자_표시정보가_실린다() {
        PostResponse response = service.createPost(authorId, new CreatePostRequest("안녕"));

        assertThat(response.content()).isEqualTo("안녕");
        assertThat(response.author().nickname()).isEqualTo("글쓴이");
        assertThat(response.likeCount()).isZero();
        assertThat(response.commentCount()).isZero();
        assertThat(response.likedByMe()).isFalse();
    }

    @Test
    void 단건_조회는_좋아요수와_내좋아요를_반영한다() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        postLikeRepository.save(PostLike.create(authorId, created.id()));

        PostResponse response = service.getPost(authorId, created.id());

        assertThat(response.likeCount()).isEqualTo(1);
        assertThat(response.likedByMe()).isTrue();
    }

    @Test
    void 삭제된_게시글_조회는_POST_NOT_FOUND() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        service.deletePost(authorId, false, created.id());

        assertThatThrownBy(() -> service.getPost(authorId, created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 타임라인은_최신순_커서페이지를_돌려준다() {
        PostResponse p1 = service.createPost(authorId, new CreatePostRequest("1"));
        PostResponse p2 = service.createPost(authorId, new CreatePostRequest("2"));
        PostResponse p3 = service.createPost(authorId, new CreatePostRequest("3"));

        CursorPage<PostResponse> firstPage = service.getTimeline(authorId, null, 2);
        assertThat(firstPage.items()).extracting(PostResponse::id)
                .containsExactly(p3.id(), p2.id());
        assertThat(firstPage.nextCursor()).isEqualTo(p2.id());

        CursorPage<PostResponse> secondPage = service.getTimeline(authorId, firstPage.nextCursor(), 2);
        assertThat(secondPage.items()).extracting(PostResponse::id).containsExactly(p1.id());
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 작성자가_아니면_수정은_FORBIDDEN() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        Member other = memberRepository.save(Member.createLocal("o", "pw", "o@x.com", "남"));

        assertThatThrownBy(() -> service.editPost(other.getId(), created.id(),
                new UpdatePostRequest("바꿈")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 작성자는_수정할_수_있다() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("이전"));

        PostResponse updated = service.editPost(authorId, created.id(), new UpdatePostRequest("이후"));

        assertThat(updated.content()).isEqualTo("이후");
    }

    @Test
    void 타인_삭제는_FORBIDDEN이지만_어드민은_가능하다() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        Member other = memberRepository.save(Member.createLocal("o2", "pw", "o2@x.com", "남2"));

        assertThatThrownBy(() -> service.deletePost(other.getId(), false, created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deletePost(other.getId(), true, created.id()); // 어드민 모더레이션
        assertThat(postRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.service.FeedPostServiceTest"`
Expected: FAIL — `ErrorCode.POST_NOT_FOUND`/DTO/`FeedPostService` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`ErrorCode.java` — VERIFIED-PERFORMER 블록 뒤에 추가:
```java
    // --- FEED ---
    POST_NOT_FOUND("404-05", HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND("404-06", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
```
(주의: enum 마지막 상수의 세미콜론 위치. 기존 마지막 상수 `FILE_UPLOAD_FAILED(...)` 뒤 `;` 앞에 추가하거나, 위 블록을 그 앞줄에 넣고 세미콜론을 유지한다.)

`CreatePostRequest.java`:
```java
package com.back.domain.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "게시글은 2000자를 넘을 수 없습니다.") String content) {
}
```

`UpdatePostRequest.java`:
```java
package com.back.domain.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "게시글은 2000자를 넘을 수 없습니다.") String content) {
}
```

`CursorPage.java`:
```java
package com.back.domain.feed.dto;

import java.util.List;

/** 커서 기반 목록 응답. nextCursor 가 null 이면 마지막 페이지. */
public record CursorPage<T>(List<T> items, Long nextCursor) {
}
```

`PostResponse.java`:
```java
package com.back.domain.feed.dto;

import com.back.domain.member.dto.MemberDisplay;
import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        MemberDisplay author,
        String content,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
```

`FeedPostService.java`:
```java
package com.back.domain.feed.service;

import com.back.domain.feed.dto.CreatePostRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.dto.PostResponse;
import com.back.domain.feed.dto.UpdatePostRequest;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.IdCount;
import com.back.domain.feed.repository.PostLikeRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글 작성/조회/타임라인/수정/삭제. 작성자 표시정보는 MEMBER 협력으로 파생한다. */
@Service
@RequiredArgsConstructor
public class FeedPostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final MemberQueryService memberQueryService;

    @Transactional
    public PostResponse createPost(Long authorId, CreatePostRequest request) {
        Post saved = postRepository.save(Post.create(authorId, request.content()));
        return toSingleResponse(authorId, saved);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(Long viewerId, Long postId) {
        Post post = findActivePost(postId);
        return toSingleResponse(viewerId, post);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getTimeline(Long viewerId, Long cursor, int size) {
        List<Post> posts = postRepository.findTimeline(cursor, PageRequest.of(0, size + 1));
        boolean hasNext = posts.size() > size;
        List<Post> page = hasNext ? posts.subList(0, size) : posts;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Set<Long> authorIds = page.stream().map(Post::getAuthorId).collect(Collectors.toSet());

        Map<Long, MemberDisplay> authors = memberQueryService.findDisplaysByIds(authorIds);
        Map<Long, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByPostIds(postIds));
        Set<Long> likedIds = Set.copyOf(postLikeRepository.findLikedPostIds(viewerId, postIds));

        List<PostResponse> items = page.stream()
                .map(p -> toResponse(p, authors.get(p.getAuthorId()),
                        likeCounts.getOrDefault(p.getId(), 0L),
                        commentCounts.getOrDefault(p.getId(), 0L),
                        likedIds.contains(p.getId())))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public PostResponse editPost(Long editorId, Long postId, UpdatePostRequest request) {
        Post post = findActivePost(postId);
        if (!post.getAuthorId().equals(editorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.edit(request.content());
        return toSingleResponse(editorId, post);
    }

    @Transactional
    public void deletePost(Long requesterId, boolean requesterIsAdmin, Long postId) {
        Post post = findActivePost(postId);
        if (!post.getAuthorId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.delete();
    }

    private Post findActivePost(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private PostResponse toSingleResponse(Long viewerId, Post post) {
        MemberDisplay author = memberQueryService.findDisplaysByIds(Set.of(post.getAuthorId()))
                .get(post.getAuthorId());
        long likeCount = postLikeRepository.countByPostId(post.getId());
        long commentCount = commentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
        boolean likedByMe = postLikeRepository.existsByMemberIdAndPostId(viewerId, post.getId());
        return toResponse(post, author, likeCount, commentCount, likedByMe);
    }

    private PostResponse toResponse(Post post, MemberDisplay author, long likeCount,
            long commentCount, boolean likedByMe) {
        return new PostResponse(post.getId(), author, post.getContent(), likeCount, commentCount,
                likedByMe, post.getCreatedAt(), post.getUpdatedAt());
    }

    private Map<Long, Long> toCountMap(List<IdCount> counts) {
        return counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCount));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.service.FeedPostServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/global/exception/ErrorCode.java
git add BE/src/main/java/com/back/domain/feed/dto/ BE/src/main/java/com/back/domain/feed/service/FeedPostService.java
git add BE/src/test/java/com/back/domain/feed/service/FeedPostServiceTest.java
git commit -m "feat: FEED 게시글 서비스(작성/조회/타임라인/수정/삭제) + 에러코드 404-05/06"
```

---

## Task 8: FeedCommentService + CommentResponse + CreateCommentRequest

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/dto/CreateCommentRequest.java`
- Create: `BE/src/main/java/com/back/domain/feed/dto/CommentResponse.java`
- Create: `BE/src/main/java/com/back/domain/feed/service/FeedCommentService.java`
- Test: `BE/src/test/java/com/back/domain/feed/service/FeedCommentServiceTest.java`

**Interfaces:**
- Consumes: `PostRepository`, `CommentRepository`, `CommentLikeRepository` (Task 4), `MemberQueryService` (Task 6), `ErrorCode.POST_NOT_FOUND/COMMENT_NOT_FOUND/FORBIDDEN`.
- Produces:
  - `CreateCommentRequest(@NotBlank @Size(max=500) String content)`
  - `CommentResponse(Long id, Long postId, MemberDisplay author, String content, long likeCount, boolean likedByMe, LocalDateTime createdAt)`
  - `FeedCommentService`: `createComment(Long authorId, Long postId, CreateCommentRequest) -> CommentResponse`; `getComments(Long viewerId, Long postId, Long cursor, int size) -> CursorPage<CommentResponse>`; `deleteComment(Long requesterId, boolean requesterIsAdmin, Long commentId) -> void`.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.feed.dto.CommentResponse;
import com.back.domain.feed.dto.CreateCommentRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FeedCommentServiceTest {

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired CommentLikeRepository commentLikeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private FeedCommentService service;
    private Long memberId;
    private Long postId;

    @BeforeEach
    void setUp() {
        VerifiedPerformerService vps = new VerifiedPerformerService(verificationApplicationRepository);
        MemberQueryService memberQueryService = new MemberQueryService(memberRepository, vps);
        service = new FeedCommentService(postRepository, commentRepository, commentLikeRepository,
                memberQueryService);
        Member member = memberRepository.save(Member.createLocal("c", "pw", "c@x.com", "댓쓴이"));
        memberId = member.getId();
        Post post = postRepository.save(Post.create(memberId, "글"));
        postId = post.getId();
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 댓글을_작성하면_작성자정보가_실린다() {
        CommentResponse response = service.createComment(memberId, postId,
                new CreateCommentRequest("좋은 글"));

        assertThat(response.content()).isEqualTo("좋은 글");
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.author().nickname()).isEqualTo("댓쓴이");
    }

    @Test
    void 없는_게시글에_댓글은_POST_NOT_FOUND() {
        assertThatThrownBy(() -> service.createComment(memberId, 999999L,
                new CreateCommentRequest("x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 댓글_목록은_오래된순_커서페이지다() {
        CommentResponse c1 = service.createComment(memberId, postId, new CreateCommentRequest("1"));
        CommentResponse c2 = service.createComment(memberId, postId, new CreateCommentRequest("2"));
        CommentResponse c3 = service.createComment(memberId, postId, new CreateCommentRequest("3"));

        CursorPage<CommentResponse> firstPage = service.getComments(memberId, postId, null, 2);
        assertThat(firstPage.items()).extracting(CommentResponse::id)
                .containsExactly(c1.id(), c2.id());
        assertThat(firstPage.nextCursor()).isEqualTo(c2.id());

        CursorPage<CommentResponse> secondPage =
                service.getComments(memberId, postId, firstPage.nextCursor(), 2);
        assertThat(secondPage.items()).extracting(CommentResponse::id).containsExactly(c3.id());
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 타인_댓글삭제는_FORBIDDEN이고_본인은_가능하다() {
        CommentResponse comment = service.createComment(memberId, postId,
                new CreateCommentRequest("내 댓글"));
        Member other = memberRepository.save(Member.createLocal("o", "pw", "o@x.com", "남"));

        assertThatThrownBy(() -> service.deleteComment(other.getId(), false, comment.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deleteComment(memberId, false, comment.id());
        assertThat(commentRepository.findByIdAndDeletedAtIsNull(comment.id())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.service.FeedCommentServiceTest"`
Expected: FAIL — DTO/`FeedCommentService` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`CreateCommentRequest.java`:
```java
package com.back.domain.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 500, message = "댓글은 500자를 넘을 수 없습니다.") String content) {
}
```

`CommentResponse.java`:
```java
package com.back.domain.feed.dto;

import com.back.domain.member.dto.MemberDisplay;
import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        MemberDisplay author,
        String content,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt) {
}
```

`FeedCommentService.java`:
```java
package com.back.domain.feed.service;

import com.back.domain.feed.dto.CommentResponse;
import com.back.domain.feed.dto.CreateCommentRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.entity.Comment;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.IdCount;
import com.back.domain.feed.repository.PostRepository;
import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평면 댓글 작성/목록/삭제. 대상 게시글 존재(미삭제)를 서비스에서 확인한다. */
@Service
@RequiredArgsConstructor
public class FeedCommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final MemberQueryService memberQueryService;

    @Transactional
    public CommentResponse createComment(Long authorId, Long postId, CreateCommentRequest request) {
        requireActivePost(postId);
        Comment saved = commentRepository.save(Comment.create(postId, authorId, request.content()));
        MemberDisplay author = memberQueryService.findDisplaysByIds(Set.of(authorId)).get(authorId);
        return toResponse(saved, author, 0L, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> getComments(Long viewerId, Long postId, Long cursor,
            int size) {
        requireActivePost(postId);
        List<Comment> comments =
                commentRepository.findCommentPage(postId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = comments.size() > size;
        List<Comment> page = hasNext ? comments.subList(0, size) : comments;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        List<Long> commentIds = page.stream().map(Comment::getId).toList();
        Set<Long> authorIds = page.stream().map(Comment::getAuthorId).collect(Collectors.toSet());

        Map<Long, MemberDisplay> authors = memberQueryService.findDisplaysByIds(authorIds);
        Map<Long, Long> likeCounts = commentLikeRepository.countByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCount));
        Set<Long> likedIds =
                Set.copyOf(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));

        List<CommentResponse> items = page.stream()
                .map(c -> toResponse(c, authors.get(c.getAuthorId()),
                        likeCounts.getOrDefault(c.getId(), 0L), likedIds.contains(c.getId())))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public void deleteComment(Long requesterId, boolean requesterIsAdmin, Long commentId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getAuthorId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.delete();
    }

    private void requireActivePost(Long postId) {
        if (postRepository.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private CommentResponse toResponse(Comment comment, MemberDisplay author, long likeCount,
            boolean likedByMe) {
        return new CommentResponse(comment.getId(), comment.getPostId(), author,
                comment.getContent(), likeCount, likedByMe, comment.getCreatedAt());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.service.FeedCommentServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/dto/CreateCommentRequest.java BE/src/main/java/com/back/domain/feed/dto/CommentResponse.java BE/src/main/java/com/back/domain/feed/service/FeedCommentService.java
git add BE/src/test/java/com/back/domain/feed/service/FeedCommentServiceTest.java
git commit -m "feat: FEED 댓글 서비스(작성/목록/삭제) 추가"
```

---

## Task 9: FeedLikeService (게시글/댓글 좋아요·취소, 멱등)

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/service/FeedLikeService.java`
- Test: `BE/src/test/java/com/back/domain/feed/service/FeedLikeServiceTest.java`

**Interfaces:**
- Consumes: `PostRepository`, `CommentRepository`, `PostLikeRepository`, `CommentLikeRepository` (Task 4), `ErrorCode.POST_NOT_FOUND/COMMENT_NOT_FOUND`.
- Produces: `FeedLikeService`: `likePost(Long memberId, Long postId)`; `unlikePost(Long memberId, Long postId)`; `likeComment(Long memberId, Long commentId)`; `unlikeComment(Long memberId, Long commentId)` — 모두 멱등, void.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.feed.entity.Comment;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostLikeRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FeedLikeServiceTest {

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired CommentLikeRepository commentLikeRepository;

    private FeedLikeService service;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        service = new FeedLikeService(postRepository, commentRepository, postLikeRepository,
                commentLikeRepository);
        Post post = postRepository.save(Post.create(1L, "글"));
        postId = post.getId();
        Comment comment = commentRepository.save(Comment.create(postId, 1L, "댓글"));
        commentId = comment.getId();
    }

    @Test
    void 게시글_좋아요는_멱등이다() {
        service.likePost(2L, postId);
        service.likePost(2L, postId); // 두 번째는 no-op

        assertThat(postLikeRepository.countByPostId(postId)).isEqualTo(1);
    }

    @Test
    void 게시글_좋아요_취소는_멱등이다() {
        service.likePost(2L, postId);

        service.unlikePost(2L, postId);
        service.unlikePost(2L, postId); // 없어도 no-op

        assertThat(postLikeRepository.countByPostId(postId)).isZero();
    }

    @Test
    void 없는_게시글_좋아요는_POST_NOT_FOUND() {
        assertThatThrownBy(() -> service.likePost(2L, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(((BusinessException) t).getErrorCode())
                        .isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 댓글_좋아요는_멱등이다() {
        service.likeComment(2L, commentId);
        service.likeComment(2L, commentId);

        assertThat(commentLikeRepository.countByCommentId(commentId)).isEqualTo(1);
    }

    @Test
    void 없는_댓글_좋아요는_COMMENT_NOT_FOUND() {
        assertThatThrownBy(() -> service.likeComment(2L, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(((BusinessException) t).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.service.FeedLikeServiceTest"`
Expected: FAIL — `FeedLikeService` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`FeedLikeService.java`:
```java
package com.back.domain.feed.service;

import com.back.domain.feed.entity.CommentLike;
import com.back.domain.feed.entity.PostLike;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostLikeRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글/댓글 좋아요·취소. 모두 멱등(중복 좋아요·중복 취소는 no-op). */
@Service
@RequiredArgsConstructor
public class FeedLikeService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;

    @Transactional
    public void likePost(Long memberId, Long postId) {
        requireActivePost(postId);
        if (!postLikeRepository.existsByMemberIdAndPostId(memberId, postId)) {
            postLikeRepository.save(PostLike.create(memberId, postId));
        }
    }

    @Transactional
    public void unlikePost(Long memberId, Long postId) {
        postLikeRepository.deleteByMemberIdAndPostId(memberId, postId);
    }

    @Transactional
    public void likeComment(Long memberId, Long commentId) {
        requireActiveComment(commentId);
        if (!commentLikeRepository.existsByMemberIdAndCommentId(memberId, commentId)) {
            commentLikeRepository.save(CommentLike.create(memberId, commentId));
        }
    }

    @Transactional
    public void unlikeComment(Long memberId, Long commentId) {
        commentLikeRepository.deleteByMemberIdAndCommentId(memberId, commentId);
    }

    private void requireActivePost(Long postId) {
        if (postRepository.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void requireActiveComment(Long commentId) {
        if (commentRepository.findByIdAndDeletedAtIsNull(commentId).isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.service.FeedLikeServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/service/FeedLikeService.java BE/src/test/java/com/back/domain/feed/service/FeedLikeServiceTest.java
git commit -m "feat: FEED 좋아요 서비스(게시글/댓글, 멱등) 추가"
```

---

## Task 10: 컨트롤러 3종 + 어드민 판정

**Files:**
- Create: `BE/src/main/java/com/back/domain/feed/controller/FeedPostController.java`
- Create: `BE/src/main/java/com/back/domain/feed/controller/FeedCommentController.java`
- Create: `BE/src/main/java/com/back/domain/feed/controller/FeedLikeController.java`
- Test: `BE/src/test/java/com/back/domain/feed/controller/FeedControllerTest.java`

**Interfaces:**
- Consumes: `FeedPostService`, `FeedCommentService`, `FeedLikeService` (Tasks 7–9), `ApiResponse`, `@AuthenticationPrincipal Long`.
- 어드민 판정: 컨트롤러가 `org.springframework.security.core.Authentication` 의 authorities 에서 `ROLE_ADMIN` 포함 여부로 `isAdmin` 을 계산해 삭제 서비스에 전달.

- [ ] **Step 1: Write the failing test**

```java
package com.back.domain.feed.controller;

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
class FeedControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtProvider jwtProvider;

    private String authorBearer;
    private Long authorId;
    private String otherBearer;
    private String adminBearer;

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.createLocal("w", "pw", "w@x.com", "글쓴이"));
        Member other = memberRepository.save(Member.createLocal("o", "pw", "o@x.com", "남"));
        authorId = author.getId();
        authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), Role.USER);
        otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), Role.USER);
        adminBearer = "Bearer " + jwtProvider.createAccessToken(9999L, Role.ADMIN);
    }

    private String createPost() throws Exception {
        String json = mockMvc.perform(post("/api/feed/posts").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"글\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void 토큰_없이_작성은_401() throws Exception {
        mockMvc.perform(post("/api/feed/posts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"글\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게시글_작성과_타임라인_조회() throws Exception {
        createPost();
        mockMvc.perform(get("/api/feed/posts").header("Authorization", authorBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("글"))
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("글쓴이"));
    }

    @Test
    void 빈_내용_작성은_400() throws Exception {
        mockMvc.perform(post("/api/feed/posts").header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 타인_게시글_수정은_403() throws Exception {
        String id = createPost();
        mockMvc.perform(put("/api/feed/posts/" + id).header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"해킹\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void 어드민은_타인_게시글을_삭제할_수_있다() throws Exception {
        String id = createPost();
        mockMvc.perform(delete("/api/feed/posts/" + id).header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/feed/posts/" + id).header("Authorization", authorBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.resultCode").value("404-05"));
    }

    @Test
    void 댓글_작성과_좋아요() throws Exception {
        String id = createPost();
        mockMvc.perform(post("/api/feed/posts/" + id + "/comments")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("댓글"));

        mockMvc.perform(post("/api/feed/posts/" + id + "/like").header("Authorization", otherBearer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/posts/" + id).header("Authorization", otherBearer))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.controller.FeedControllerTest"`
Expected: FAIL — 컨트롤러 없음(404/401 등 불일치).

- [ ] **Step 3: Write minimal implementation**

`FeedPostController.java`:
```java
package com.back.domain.feed.controller;

import com.back.domain.feed.dto.CreatePostRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.dto.PostResponse;
import com.back.domain.feed.dto.UpdatePostRequest;
import com.back.domain.feed.service.FeedPostService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** 게시글 API. 모두 인증 필요(principal = memberId). */
@RestController
@RequestMapping("/api/feed/posts")
@RequiredArgsConstructor
public class FeedPostController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FeedPostService feedPostService;

    @PostMapping
    public ApiResponse<PostResponse> create(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(feedPostService.createPost(memberId, request));
    }

    @GetMapping
    public ApiResponse<CursorPage<PostResponse>> timeline(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(feedPostService.getTimeline(memberId, cursor, clamp(size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostResponse> get(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(feedPostService.getPost(memberId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PostResponse> edit(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.success(feedPostService.editPost(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        feedPostService.deletePost(memberId, isAdmin(authentication), id);
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

`FeedCommentController.java`:
```java
package com.back.domain.feed.controller;

import com.back.domain.feed.dto.CommentResponse;
import com.back.domain.feed.dto.CreateCommentRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.service.FeedCommentService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 댓글 API. 모두 인증 필요. */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedCommentController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FeedCommentService feedCommentService;

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentResponse> create(@AuthenticationPrincipal Long memberId,
            @PathVariable Long postId, @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(feedCommentService.createComment(memberId, postId, request));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<CursorPage<CommentResponse>> list(@AuthenticationPrincipal Long memberId,
            @PathVariable Long postId, @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(
                feedCommentService.getComments(memberId, postId, cursor, clamp(size)));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        feedCommentService.deleteComment(memberId, FeedPostController.isAdmin(authentication), id);
        return ApiResponse.success();
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
```

`FeedLikeController.java`:
```java
package com.back.domain.feed.controller;

import com.back.domain.feed.service.FeedLikeService;
import com.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 좋아요 API(멱등). 응답은 빈 성공. */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedLikeController {

    private final FeedLikeService feedLikeService;

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Void> likePost(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.likePost(memberId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<Void> unlikePost(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.unlikePost(memberId, id);
        return ApiResponse.success();
    }

    @PostMapping("/comments/{id}/like")
    public ApiResponse<Void> likeComment(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.likeComment(memberId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/comments/{id}/like")
    public ApiResponse<Void> unlikeComment(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.unlikeComment(memberId, id);
        return ApiResponse.success();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p BE test --tests "com.back.domain.feed.controller.FeedControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/domain/feed/controller/ BE/src/test/java/com/back/domain/feed/controller/FeedControllerTest.java
git commit -m "feat: FEED 컨트롤러 3종(게시글/댓글/좋아요) 추가"
```

---

## Task 11: 전체 회귀 + 문서 반영

**Files:**
- Modify: `docs/CONTEXT.md`, `docs/TODO-BACKLOG.md`, `docs/TODO-DONE.md`, `docs/AI-ACTION-LOGS.md`, `docs/DOMAIN-FEED-STATUTE.md`(§11 확정 반영)

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew -p BE test`
Expected: BUILD SUCCESSFUL. 실패 시 해당 태스크로 돌아가 수정.

- [ ] **Step 2: 문서 반영**
- CONTEXT: 구현된 도메인에 FEED 추가, API/에러코드 요약 1줄.
- TODO-BACKLOG: FEED 항목 완료 처리.
- TODO-DONE: 2026-07-17 FEED BE 완료 기록.
- AI-ACTION-LOGS: 구현 로그.
- DOMAIN-FEED-STATUTE §11: 확정값 반영(size 기본 20/최대 50, 좋아요 응답=빈 성공, MemberQueryService 신설, MemberDisplay 위치 member.dto).

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: FEED BE 구현 반영(상태/TODO/로그/STATUTE 확정)"
```

---

## 확정한 세부값 (STATUTE §11 대응)

- 커서 기본 size=20, 최대 50(초과 시 clamp).
- 좋아요 토글 응답 = 빈 성공(`ApiResponse.success()`). 카운트는 조회에서 제공.
- 작성자 배치 조회 위치 = MEMBER에 `MemberQueryService` 신설, `MemberDisplay`는 `member.dto`.
- 인증 뱃지 N+1 방지 = `VerifiedPerformerService.findVerifiedMemberIds(Set)` 배치.
- 게시글 단건 조회는 댓글을 포함하지 않음(댓글은 별도 커서 엔드포인트).
- content 정규화는 별도 트림 없이 `@NotBlank`로 공백-only만 차단(초안 유지).
