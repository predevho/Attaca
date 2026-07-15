package com.back.global.storage;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByStorageKey(String storageKey);
}
