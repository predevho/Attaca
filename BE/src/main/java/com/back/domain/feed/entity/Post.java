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
