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
