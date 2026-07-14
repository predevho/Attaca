package com.back.global.storage;

import com.back.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드된 파일의 메타데이터. 모든 업로드를 한 테이블에 기록해 고아 파일 정리와 감사가 가능하게 한다.
 * {@code uploaderId}는 {@code Member} 연관관계가 아닌 원시 값이다.
 * {@code global}이 {@code domain}을 의존하면 의존 방향이 뒤집히기 때문이다.
 */
@Entity
@Table(name = "file_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileMetadata extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type")
    private String contentType;

    /** 컬럼명을 file_size로 두는 이유: size는 일부 DB에서 예약어로 취급될 수 있다. */
    @Column(name = "file_size", nullable = false)
    private long size;

    @Column(name = "uploader_id")
    private Long uploaderId;

    private FileMetadata(String storageKey, String originalName, String contentType,
                         long size, Long uploaderId) {
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.uploaderId = uploaderId;
    }

    public static FileMetadata create(String storageKey, String originalName, String contentType,
                                      long size, Long uploaderId) {
        return new FileMetadata(storageKey, originalName, contentType, size, uploaderId);
    }
}
