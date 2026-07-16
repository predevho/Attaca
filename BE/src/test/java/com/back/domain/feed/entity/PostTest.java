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
