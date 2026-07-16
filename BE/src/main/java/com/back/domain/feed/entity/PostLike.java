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
