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
