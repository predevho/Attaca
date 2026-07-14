package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class FileMetadataRepositoryTest {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Test
    void storageKey로_조회한다() {
        fileMetadataRepository.save(FileMetadata.create(
                "profile/2026/07/14/abc.png", "내 사진.png", "image/png", 1234L, 7L));

        assertThat(fileMetadataRepository.findByStorageKey("profile/2026/07/14/abc.png"))
                .get()
                .satisfies(meta -> {
                    assertThat(meta.getOriginalName()).isEqualTo("내 사진.png");
                    assertThat(meta.getContentType()).isEqualTo("image/png");
                    assertThat(meta.getSize()).isEqualTo(1234L);
                    assertThat(meta.getUploaderId()).isEqualTo(7L);
                    assertThat(meta.getCreatedAt()).isNotNull();
                });

        assertThat(fileMetadataRepository.findByStorageKey("none")).isEmpty();
    }

    @Test
    void 업로더가_없어도_저장된다() {
        FileMetadata saved = fileMetadataRepository.save(FileMetadata.create(
                "misc/2026/07/14/anon.png", "anon.png", "image/png", 10L, null));

        assertThat(saved.getUploaderId()).isNull();
    }

    @Test
    void 같은_storageKey는_중복_저장할_수_없다() {
        fileMetadataRepository.saveAndFlush(FileMetadata.create(
                "profile/2026/07/14/dup.png", "a.png", "image/png", 1L, null));

        assertThatThrownBy(() -> fileMetadataRepository.saveAndFlush(FileMetadata.create(
                "profile/2026/07/14/dup.png", "b.png", "image/png", 2L, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
