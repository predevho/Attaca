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
