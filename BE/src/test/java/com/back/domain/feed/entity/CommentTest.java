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
