package com.back.global.storage;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크 파일 저장소. S3 자격증명 없이 개발/테스트를 돌리기 위한 기본 구현체다.
 * {@code storage.type}이 없거나 {@code local}이면 이 빈이 선택된다.
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path rootDir;
    private final String baseUrl;

    public LocalFileStorage(StorageProperties properties) {
        this.rootDir = Paths.get(properties.local().rootDir()).toAbsolutePath().normalize();
        this.baseUrl = properties.local().baseUrl();
    }

    @Override
    public String upload(String key, InputStream content, long size, String contentType) {
        Path target = resolveSafely(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
        return key;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafely(key));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
    }

    @Override
    public String getUrl(String key) {
        return FileStorage.joinUrl(baseUrl, key);
    }

    /**
     * key를 저장 루트 기준으로 해석한다.
     * 정규화 결과가 루트를 벗어나면(예: {@code ../}) 경로 탈출로 보고 거절한다.
     */
    private Path resolveSafely(String key) {
        Path target = rootDir.resolve(key).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        return target;
    }
}
