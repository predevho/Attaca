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
