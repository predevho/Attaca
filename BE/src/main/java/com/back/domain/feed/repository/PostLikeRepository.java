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
