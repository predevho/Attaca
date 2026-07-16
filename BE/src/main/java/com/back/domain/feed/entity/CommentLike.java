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
