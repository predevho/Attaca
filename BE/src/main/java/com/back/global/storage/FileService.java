package com.back.global.storage;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장 파사드. key 생성 → {@link FileStorage} 저장 → {@link FileMetadata} 영속화를 한 트랜잭션으로 묶는다.
 * 도메인 서비스는 {@link FileStorage}가 아니라 이 클래스를 사용한다.
 *
 * <p>허용 contentType·최대 크기 같은 도메인별 정책은 검증하지 않는다.
 * 프로필 이미지가 이미지 타입만 받는지는 MEMBER 도메인이 판단할 일이다.
 * 전역 상한은 {@code spring.servlet.multipart.max-file-size}로 건다.
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileStorage fileStorage;
    private final FileMetadataRepository fileMetadataRepository;

    @Transactional
    public StoredFile upload(MultipartFile file, String directory, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        String key = generateKey(directory, originalName);

        try (InputStream content = file.getInputStream()) {
            fileStorage.upload(key, content, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }

        FileMetadata saved = fileMetadataRepository.save(FileMetadata.create(
                key, originalName, file.getContentType(), file.getSize(), uploaderId));

        return new StoredFile(saved.getId(), key, fileStorage.getUrl(key));
    }

    /**
     * 저장된 파일을 지운다. 메타데이터에 없는 key는 호출 측 실수이므로 알린다.
     * 반면 그 아래 {@link FileStorage#delete}는 물리 파일이 이미 없어도 조용히 넘어간다(멱등).
     */
    @Transactional
    public void delete(String storageKey) {
        FileMetadata metadata = fileMetadataRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        fileStorage.delete(storageKey);
        fileMetadataRepository.delete(metadata);
    }

    public String getUrl(String storageKey) {
        return fileStorage.getUrl(storageKey);
    }

    /**
     * key 형식: {@code {디렉터리}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}}
     *
     * <p>날짜를 접두사로 넣어 나중에 오래된 파일을 접두사로 골라낼 수 있게 한다.
     * 원본 파일명은 key에 넣지 않는다 — 한글·공백·특수문자가 URL로 새어나가기 때문이다.
     */
    private static String generateKey(String directory, String originalName) {
        LocalDate today = LocalDate.now();
        return "%s/%04d/%02d/%02d/%s%s".formatted(
                directory,
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID(), extensionOf(originalName));
    }

    /** 확장자를 점을 포함해 반환한다(예: {@code .png}). 확장자가 없으면 빈 문자열. */
    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
