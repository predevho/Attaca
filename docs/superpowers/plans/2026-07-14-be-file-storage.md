# BE 파일 저장 기반(FileStorage) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 파일 저장을 `FileStorage` 인터페이스로 추상화하고, 로컬 디스크 구현체(기본값)와 S3 구현체를 제공한다. 도메인이 S3 SDK나 물리 경로를 직접 다루지 않게 한다.

**Architecture:** `FileStorage`는 바이트만 다루고 DB를 모른다(`upload`/`delete`/`getUrl`). 그 위의 `FileService`가 key 생성 → `FileStorage` 호출 → `FileMetadata` 영속화를 한 트랜잭션으로 조합한다. 구현체는 `storage.type` 설정(`local`/`s3`)으로 `@ConditionalOnProperty`를 통해 하나만 빈으로 올라간다.

**Tech Stack:** Java 21 / Spring Boot 3.4.5 / Spring Data JPA / AWS SDK for Java v2 / JUnit 5 + AssertJ + Mockito / H2(테스트)

**설계 문서:** `docs/superpowers/specs/2026-07-14-be-file-storage-design.md`

## Global Constraints

- Spring Boot **3.4.5**, Gradle **8.11.1**, JDK **21**. Gradle 9는 Boot 3.4 미지원 — 래퍼를 올리지 말 것.
- 단순 필드 접근자는 Lombok `@Getter`로 통일. 수동 getter 금지. (`DOMAIN-COMMON-STATUTE §8`)
- 모든 엔티티는 `BaseEntity`를 상속한다. (`DOMAIN-COMMON-STATUTE §3`)
- 엔티티는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `private` 생성자 + `static create(...)` 팩토리 패턴을 따른다. (기존 `Member`/`SocialAccount`와 동일)
- `ErrorCode.resultCode`는 `HTTP상태-일련번호` 문자열. 기존 번호와 충돌 금지.
- 자격증명(`S3_BUCKET`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)은 **env 주입, 커밋 금지.**
- `global`은 `domain`을 의존하지 않는다. (의존 방향: `global` ← `domain`)
- 커밋 메시지는 한글. `유형: 한글 요약` 형태.
- 각 태스크 끝에서 `cd BE && ./gradlew clean build`가 통과해야 한다.
- **실제 AWS 호출은 자동 테스트에 넣지 않는다.** `S3Client`는 항상 목으로 대체한다.

---

## File Structure

**신규 생성 — `com.back.global.storage`**

| 파일 | 책임 |
|---|---|
| `FileStorage.java` | 바이트 저장 인터페이스. DB를 모른다. |
| `LocalFileStorage.java` | 로컬 디스크 구현체 (기본값) |
| `S3FileStorage.java` | AWS S3 구현체 |
| `StorageProperties.java` | `storage.*` 설정 바인딩 |
| `FileMetadata.java` | 업로드 파일 메타데이터 엔티티 |
| `FileMetadataRepository.java` | 메타데이터 리포지토리 |
| `FileService.java` | key 생성 + 저장 + 메타데이터를 조합하는 파사드 |
| `StoredFile.java` | 업로드 결과 record |

**신규 생성 — `com.back.global.config`**

| 파일 | 책임 |
|---|---|
| `StorageConfig.java` | `S3Client` 빈 (`storage.type=s3`일 때만) |
| `LocalFileServingConfig.java` | `/files/**` 정적 리소스 핸들러 (`storage.type=local`일 때만) |

**수정**

| 파일 | 변경 |
|---|---|
| `BE/build.gradle.kts` | AWS SDK v2 BOM + s3 의존성 |
| `global/exception/ErrorCode.java` | `INVALID_FILE`/`FILE_NOT_FOUND`/`FILE_UPLOAD_FAILED` 추가 |
| `global/exception/BusinessException.java` | `cause`를 받는 생성자 추가 |
| `global/security/SecurityConfig.java` | `/files/**` permitAll |
| `BE/src/main/resources/application.yaml` | `storage.*` 블록 |
| `.gitignore` | `BE/uploads/` |

---

## 스펙과의 차이 (의도적)

설계 문서 §3-4는 `FileService.upload(MultipartFile, String directory)`로 적었지만, §3-5의 `FileMetadata.uploaderId`를 채우려면 업로더가 필요하다. 이 계획은 **`upload(MultipartFile file, String directory, Long uploaderId)`** 3-인자 형태를 쓴다. `uploaderId`는 nullable이므로 익명 업로드도 `null`로 가능하다.

---

## Task 1: 에러 코드와 예외 원인 보존

파일 저장 계층이 던질 에러 코드를 먼저 만든다. 구현체의 `IOException`/`SdkException`을 감쌀 때 원인 예외를 잃지 않도록 `BusinessException`에 `cause` 생성자를 추가한다.

**Files:**
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java`
- Modify: `BE/src/main/java/com/back/global/exception/BusinessException.java`
- Test: `BE/src/test/java/com/back/global/exception/ErrorCodeTest.java` (기존 파일에 추가)
- Test: `BE/src/test/java/com/back/global/exception/BusinessExceptionTest.java` (기존 파일에 추가)

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `ErrorCode.INVALID_FILE` (`"400-02"`, `HttpStatus.BAD_REQUEST`)
  - `ErrorCode.FILE_NOT_FOUND` (`"404-01"`, `HttpStatus.NOT_FOUND`)
  - `ErrorCode.FILE_UPLOAD_FAILED` (`"500-02"`, `HttpStatus.INTERNAL_SERVER_ERROR`)
  - `BusinessException(ErrorCode errorCode, Throwable cause)`

- [ ] **Step 1: 실패하는 테스트 작성**

`ErrorCodeTest.java`에 아래 테스트를 추가한다 (기존 테스트는 그대로 둔다).

```java
    @Test
    void 파일_에러코드는_지정된_resultCode와_상태를_가진다() {
        assertThat(ErrorCode.INVALID_FILE.getResultCode()).isEqualTo("400-02");
        assertThat(ErrorCode.INVALID_FILE.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_FILE.getCode()).isEqualTo("INVALID_FILE");

        assertThat(ErrorCode.FILE_NOT_FOUND.getResultCode()).isEqualTo("404-01");
        assertThat(ErrorCode.FILE_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(ErrorCode.FILE_UPLOAD_FAILED.getResultCode()).isEqualTo("500-02");
        assertThat(ErrorCode.FILE_UPLOAD_FAILED.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
```

`BusinessExceptionTest.java`에 아래 테스트를 추가한다.

```java
    @Test
    void 원인_예외를_보존한다() {
        IOException cause = new IOException("디스크 오류");

        BusinessException ex = new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, cause);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.FILE_UPLOAD_FAILED.getMessage());
        assertThat(ex.getCause()).isSameAs(cause);
    }
```

필요한 import(`java.io.IOException`, `org.springframework.http.HttpStatus`)를 각 테스트 파일에 추가한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.exception.*"
```

Expected: 컴파일 실패 — `INVALID_FILE` 심볼을 찾을 수 없고 `BusinessException(ErrorCode, Throwable)` 생성자가 없다.

- [ ] **Step 3: 최소 구현**

`ErrorCode.java`의 마지막 상수(`OAUTH_PROVIDER_ERROR`) 뒤에 추가한다. 마지막 상수의 세미콜론 위치를 옮기는 것에 주의한다.

```java
    // --- 파일 저장 ---
    INVALID_FILE("400-02", HttpStatus.BAD_REQUEST, "올바르지 않은 파일입니다."),
    FILE_NOT_FOUND("404-01", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_UPLOAD_FAILED("500-02", HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");
```

`BusinessException.java`에 생성자를 추가한다.

```java
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.exception.*"
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/global/exception/ BE/src/test/java/com/back/global/exception/
git commit -m "feat: 파일 저장 에러코드 3종 추가 및 예외 원인 보존 생성자 추가"
```

---

## Task 2: 설정 바인딩 (`StorageProperties`)

`storage.*` 설정을 바인딩한다. `AttacaApplication`에 이미 `@ConfigurationPropertiesScan`이 붙어 있으므로 별도 등록은 필요 없다.

**Files:**
- Create: `BE/src/main/java/com/back/global/storage/StorageProperties.java`
- Modify: `BE/src/main/resources/application.yaml`
- Modify: `.gitignore`
- Test: `BE/src/test/java/com/back/global/storage/StoragePropertiesTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `StorageProperties(String type, StorageProperties.Local local, StorageProperties.S3 s3)`
  - `StorageProperties.Local(String rootDir, String baseUrl)`
  - `StorageProperties.S3(String bucket, String region, String accessKey, String secretKey, String baseUrl)`

- [ ] **Step 1: 실패하는 테스트 작성**

`StoragePropertiesTest.java`:

```java
package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StoragePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void storage_설정을_바인딩한다() {
        runner.withPropertyValues(
                        "storage.type=s3",
                        "storage.local.root-dir=./uploads",
                        "storage.local.base-url=http://localhost:8080/files",
                        "storage.s3.bucket=attaca-bucket",
                        "storage.s3.region=ap-northeast-2",
                        "storage.s3.access-key=AK",
                        "storage.s3.secret-key=SK",
                        "storage.s3.base-url=https://cdn.attaca.com")
                .run(context -> {
                    StorageProperties props = context.getBean(StorageProperties.class);

                    assertThat(props.type()).isEqualTo("s3");
                    assertThat(props.local().rootDir()).isEqualTo("./uploads");
                    assertThat(props.local().baseUrl()).isEqualTo("http://localhost:8080/files");
                    assertThat(props.s3().bucket()).isEqualTo("attaca-bucket");
                    assertThat(props.s3().region()).isEqualTo("ap-northeast-2");
                    assertThat(props.s3().accessKey()).isEqualTo("AK");
                    assertThat(props.s3().secretKey()).isEqualTo("SK");
                    assertThat(props.s3().baseUrl()).isEqualTo("https://cdn.attaca.com");
                });
    }

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class TestConfig {
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.StoragePropertiesTest"
```

Expected: 컴파일 실패 — `StorageProperties` 클래스가 없다.

- [ ] **Step 3: 최소 구현**

`StorageProperties.java`:

```java
package com.back.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장 설정. {@code type}이 {@code local}이면 로컬 디스크, {@code s3}면 AWS S3를 사용한다.
 * S3 자격증명은 환경변수로 주입한다(커밋 금지).
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String type, Local local, S3 s3) {

    /** 로컬 디스크 저장 설정. */
    public record Local(String rootDir, String baseUrl) {
    }

    /** S3 저장 설정. {@code baseUrl}은 나중에 CDN 도메인으로 교체하는 지점이다. */
    public record S3(String bucket, String region, String accessKey, String secretKey, String baseUrl) {
    }
}
```

`application.yaml`의 기존 `spring:` 블록 아래에 업로드 전역 상한을 추가한다. `FileService`는 도메인별 크기 정책을 판단하지 않으므로, 상한은 여기서 한 번만 건다.

```yaml
spring:
  application:
    name: attaca
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

그리고 파일 맨 끝에 `storage` 블록을 추가한다.

```yaml

storage:
  type: ${STORAGE_TYPE:local}          # local | s3
  local:
    root-dir: ${STORAGE_LOCAL_ROOT:./uploads}
    base-url: ${STORAGE_LOCAL_BASE_URL:http://localhost:8080/files}
  s3:
    bucket: ${S3_BUCKET:}
    region: ${S3_REGION:ap-northeast-2}
    access-key: ${AWS_ACCESS_KEY_ID:}
    secret-key: ${AWS_SECRET_ACCESS_KEY:}
    base-url: ${S3_BASE_URL:}          # CloudFront/R2 교체 지점
```

`.gitignore` 맨 끝에 추가한다.

```gitignore

# 로컬 파일 저장소 (storage.type=local)
BE/uploads/
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.StoragePropertiesTest"
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/global/storage/ BE/src/test/java/com/back/global/storage/ BE/src/main/resources/application.yaml .gitignore
git commit -m "feat: 파일 저장 설정(StorageProperties) 및 환경변수 분리 추가"
```

---

## Task 3: `FileStorage` 인터페이스 + `LocalFileStorage`

바이트를 다루는 인터페이스와 기본 구현체(로컬 디스크)를 만든다. `storage.type`이 없거나 `local`이면 이 구현체가 선택된다.

**중요:** key에 `../`가 섞여 들어오면 저장 루트 밖으로 파일이 쓰일 수 있다. 정규화한 경로가 루트 밖을 가리키면 `INVALID_FILE`로 거절한다.

**Files:**
- Create: `BE/src/main/java/com/back/global/storage/FileStorage.java`
- Create: `BE/src/main/java/com/back/global/storage/LocalFileStorage.java`
- Test: `BE/src/test/java/com/back/global/storage/LocalFileStorageTest.java`

**Interfaces:**
- Consumes: `StorageProperties`(Task 2), `ErrorCode.INVALID_FILE`/`FILE_UPLOAD_FAILED`, `BusinessException(ErrorCode, Throwable)` (Task 1)
- Produces:
  - `FileStorage.upload(String key, InputStream content, long size, String contentType)` → `String` (저장된 key)
  - `FileStorage.delete(String key)` → `void`
  - `FileStorage.getUrl(String key)` → `String`
  - `FileStorage.joinUrl(String baseUrl, String key)` → `String` (static, 두 구현체가 공유)
  - `LocalFileStorage(StorageProperties properties)`

- [ ] **Step 1: 실패하는 테스트 작성**

`LocalFileStorageTest.java`:

```java
package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new StorageProperties(
                "local",
                new StorageProperties.Local(tempDir.toString(), "http://localhost:8080/files"),
                null));
    }

    private InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 파일을_저장하고_key를_반환한다() {
        String key = "profile/2026/07/14/abc.png";

        String returned = storage.upload(key, content("hello"), 5L, "image/png");

        assertThat(returned).isEqualTo(key);
        Path saved = tempDir.resolve(key);
        assertThat(saved).exists();
        assertThat(Files.readString(saved)).isEqualTo("hello");
    }

    @Test
    void 저장한_파일을_삭제한다() {
        String key = "profile/2026/07/14/abc.png";
        storage.upload(key, content("hello"), 5L, "image/png");

        storage.delete(key);

        assertThat(tempDir.resolve(key)).doesNotExist();
    }

    @Test
    void 없는_파일을_삭제해도_예외를_던지지_않는다() {
        String key = "profile/2026/07/14/does-not-exist.png";

        assertThatCode(() -> storage.delete(key)).doesNotThrowAnyException();

        assertThat(tempDir.resolve(key)).doesNotExist();
    }

    @Test
    void baseUrl과_key를_이어_URL을_만든다() {
        assertThat(storage.getUrl("profile/2026/07/14/abc.png"))
                .isEqualTo("http://localhost:8080/files/profile/2026/07/14/abc.png");
    }

    @Test
    void 루트를_벗어나는_key는_거절한다() {
        assertThatThrownBy(() -> storage.upload("../evil.png", content("bad"), 3L, "image/png"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }
}
```

`Files.readString`은 `IOException`을 던지므로 해당 테스트 메서드에 `throws Exception`을 붙인다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.LocalFileStorageTest"
```

Expected: 컴파일 실패 — `LocalFileStorage`, `FileStorage`가 없다.

- [ ] **Step 3: 최소 구현**

`FileStorage.java`:

```java
package com.back.global.storage;

import java.io.InputStream;

/**
 * 파일 바이트 저장소. 논리 key 단위로 저장/삭제/URL 생성만 담당하며 DB를 알지 못한다.
 * key 생성 규칙과 메타데이터 영속화는 {@link FileService}의 책임이다.
 */
public interface FileStorage {

    /** 주어진 key 위치에 내용을 저장하고 저장된 key를 반환한다. */
    String upload(String key, InputStream content, long size, String contentType);

    /** key에 해당하는 파일을 삭제한다. 물리 파일이 이미 없어도 예외를 던지지 않는다(멱등). */
    void delete(String key);

    /** key의 공개 접근 URL을 만든다. */
    String getUrl(String key);

    /** base-url과 key를 이어 붙인다. base-url 끝의 '/' 유무에 관계없이 결과가 같다. */
    static String joinUrl(String baseUrl, String key) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return base + "/" + key;
    }
}
```

`LocalFileStorage.java`:

```java
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
```

`size`와 `contentType`은 로컬 저장에 쓰이지 않지만, S3 구현체가 필요로 하므로 인터페이스에 유지한다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.LocalFileStorageTest"
```

Expected: PASS (5개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/global/storage/ BE/src/test/java/com/back/global/storage/
git commit -m "feat: FileStorage 인터페이스 및 LocalFileStorage 구현체 추가"
```

---

## Task 4: `S3FileStorage` + AWS SDK 의존성

AWS SDK v2로 S3 구현체를 만든다. **실제 AWS를 호출하지 않는다** — `S3Client`를 목으로 주입해 어떤 요청을 조립했는지만 검증한다. 실제 버킷 검증은 자격증명 발급 후 수동으로 한다.

**Files:**
- Modify: `BE/build.gradle.kts`
- Create: `BE/src/main/java/com/back/global/storage/S3FileStorage.java`
- Create: `BE/src/main/java/com/back/global/config/StorageConfig.java`
- Test: `BE/src/test/java/com/back/global/storage/S3FileStorageTest.java`

**Interfaces:**
- Consumes: `FileStorage`(Task 3), `StorageProperties`(Task 2), `ErrorCode.FILE_UPLOAD_FAILED`(Task 1)
- Produces: `S3FileStorage(S3Client s3Client, StorageProperties properties)`

- [ ] **Step 1: AWS SDK 의존성 추가**

`BE/build.gradle.kts`의 `dependencies` 블록에서 `implementation("org.springframework.boot:spring-boot-starter-web")` 아래에 추가한다.

```kotlin
    implementation(platform("software.amazon.awssdk:bom:2.31.16"))
    implementation("software.amazon.awssdk:s3")
```

의존성이 해석되는지 먼저 확인한다.

```bash
cd BE && ./gradlew dependencies --configuration compileClasspath | grep awssdk
```

Expected: `software.amazon.awssdk:s3` 항목이 보인다.
해석에 실패하면(버전 없음) Maven Central에서 최신 2.x BOM 버전을 확인해 바꾼다:
`https://repo1.maven.org/maven2/software/amazon/awssdk/bom/` — 목록의 최신 `2.x.y`를 쓴다.

- [ ] **Step 2: 실패하는 테스트 작성**

`S3FileStorageTest.java`:

```java
package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    @Mock
    private S3Client s3Client;

    private S3FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3FileStorage(s3Client, new StorageProperties(
                "s3",
                null,
                new StorageProperties.S3("attaca-bucket", "ap-northeast-2", "AK", "SK",
                        "https://cdn.attaca.com")));
    }

    private InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 올바른_PutObjectRequest로_업로드한다() {
        String key = "profile/2026/07/14/abc.png";

        String returned = storage.upload(key, content("hello"), 5L, "image/png");

        assertThat(returned).isEqualTo(key);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("attaca-bucket");
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("image/png");
    }

    @Test
    void 올바른_DeleteObjectRequest로_삭제한다() {
        String key = "profile/2026/07/14/abc.png";

        storage.delete(key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertThat(captor.getValue().bucket()).isEqualTo("attaca-bucket");
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void baseUrl과_key를_이어_URL을_만든다() {
        assertThat(storage.getUrl("profile/2026/07/14/abc.png"))
                .isEqualTo("https://cdn.attaca.com/profile/2026/07/14/abc.png");
    }

    @Test
    void SDK_예외를_FILE_UPLOAD_FAILED로_감싼다() {
        SdkException cause = SdkException.builder().message("s3 down").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(cause);

        assertThatThrownBy(() -> storage.upload("k.png", content("x"), 1L, "image/png"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.S3FileStorageTest"
```

Expected: 컴파일 실패 — `S3FileStorage`가 없다.

- [ ] **Step 4: 최소 구현**

`S3FileStorage.java`:

```java
package com.back.global.storage;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 파일 저장소. {@code storage.type=s3}일 때만 빈으로 등록된다.
 * 조회 URL은 {@code storage.s3.base-url} + key로 만들며, 이 설정이 나중에 CDN 도메인으로 바뀌는 지점이다.
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String baseUrl;

    public S3FileStorage(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.s3().bucket();
        this.baseUrl = properties.s3().baseUrl();
    }

    @Override
    public String upload(String key, InputStream content, long size, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(content, size));
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
        return key;
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
    }

    @Override
    public String getUrl(String key) {
        return FileStorage.joinUrl(baseUrl, key);
    }
}
```

`StorageConfig.java`:

```java
package com.back.global.config;

import com.back.global.storage.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** S3 클라이언트 설정. {@code storage.type=s3}일 때만 활성화되어 키 없이도 앱이 뜬다. */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        return S3Client.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .build();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.S3FileStorageTest"
```

Expected: PASS (4개 테스트)

- [ ] **Step 6: 커밋**

```bash
git add BE/build.gradle.kts BE/src/main/java/com/back/global/storage/S3FileStorage.java BE/src/main/java/com/back/global/config/StorageConfig.java BE/src/test/java/com/back/global/storage/S3FileStorageTest.java
git commit -m "feat: S3FileStorage 구현체 및 S3Client 설정 추가"
```

---

## Task 5: `FileMetadata` 엔티티 + 리포지토리

업로드 파일 메타데이터를 공용 테이블에 기록한다. `uploaderId`는 `Member` 연관관계가 아니라 원시 `Long`이다 — `global`이 `domain.member`를 의존하면 의존 방향이 뒤집히기 때문이다.

**Files:**
- Create: `BE/src/main/java/com/back/global/storage/FileMetadata.java`
- Create: `BE/src/main/java/com/back/global/storage/FileMetadataRepository.java`
- Test: `BE/src/test/java/com/back/global/storage/FileMetadataRepositoryTest.java`

**Interfaces:**
- Consumes: `BaseEntity`
- Produces:
  - `FileMetadata.create(String storageKey, String originalName, String contentType, long size, Long uploaderId)` → `FileMetadata`
  - getter: `getId()`, `getStorageKey()`, `getOriginalName()`, `getContentType()`, `getSize()`, `getUploaderId()`
  - `FileMetadataRepository.findByStorageKey(String storageKey)` → `Optional<FileMetadata>`

- [ ] **Step 1: 실패하는 테스트 작성**

`FileMetadataRepositoryTest.java`:

```java
package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
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
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.FileMetadataRepositoryTest"
```

Expected: 컴파일 실패 — `FileMetadata`가 없다.

- [ ] **Step 3: 최소 구현**

`FileMetadata.java`:

```java
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
```

`FileMetadataRepository.java`:

```java
package com.back.global.storage;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByStorageKey(String storageKey);
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.FileMetadataRepositoryTest"
```

Expected: PASS (3개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/global/storage/FileMetadata.java BE/src/main/java/com/back/global/storage/FileMetadataRepository.java BE/src/test/java/com/back/global/storage/FileMetadataRepositoryTest.java
git commit -m "feat: 업로드 파일 메타데이터(FileMetadata) 엔티티 및 리포지토리 추가"
```

---

## Task 6: `FileService` — key 생성 + 저장 + 메타데이터 조합

도메인이 실제로 쓰게 될 파사드다. `FileStorage`를 Fake로 갈아끼워 테스트하므로 디스크도 AWS도 건드리지 않는다.

**key 형식:** `{디렉터리}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}` — 원본 파일명은 key에 넣지 않는다(한글·특수문자가 URL로 새는 것을 막는다).

**Files:**
- Create: `BE/src/main/java/com/back/global/storage/StoredFile.java`
- Create: `BE/src/main/java/com/back/global/storage/FileService.java`
- Test: `BE/src/test/java/com/back/global/storage/FileServiceTest.java`

**Interfaces:**
- Consumes: `FileStorage`(Task 3), `FileMetadata`/`FileMetadataRepository`(Task 5), `ErrorCode.INVALID_FILE`/`FILE_NOT_FOUND`/`FILE_UPLOAD_FAILED`(Task 1)
- Produces:
  - `StoredFile(Long id, String storageKey, String url)` (record)
  - `FileService.upload(MultipartFile file, String directory, Long uploaderId)` → `StoredFile`
  - `FileService.delete(String storageKey)` → `void`
  - `FileService.getUrl(String storageKey)` → `String`

- [ ] **Step 1: 실패하는 테스트 작성**

`FileServiceTest.java`:

```java
package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

// FileStorage 를 Fake 로 대체해 디스크/AWS 를 건드리지 않고, 메타데이터 영속화는 실제 H2 로 검증한다.
@DataJpaTest
class FileServiceTest {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private FakeFileStorage fileStorage;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileStorage = new FakeFileStorage();
        fileService = new FileService(fileStorage, fileMetadataRepository);
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
                "file", "내 사진.png", "image/png", "hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 업로드하면_key와_url을_반환하고_메타데이터를_저장한다() {
        StoredFile stored = fileService.upload(pngFile(), "profile", 7L);

        LocalDate today = LocalDate.now();
        String datePrefix = "profile/%04d/%02d/%02d/"
                .formatted(today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        assertThat(stored.storageKey()).startsWith(datePrefix).endsWith(".png");
        assertThat(stored.url()).isEqualTo("http://fake/" + stored.storageKey());
        assertThat(stored.id()).isNotNull();

        // 원본 파일명(한글)은 key 에 들어가지 않는다
        assertThat(stored.storageKey()).doesNotContain("내 사진");

        // 실제 스토리지에 내용이 전달되었다
        assertThat(fileStorage.stored).containsKey(stored.storageKey());

        // 메타데이터가 저장되었다
        assertThat(fileMetadataRepository.findByStorageKey(stored.storageKey()))
                .get()
                .satisfies(meta -> {
                    assertThat(meta.getOriginalName()).isEqualTo("내 사진.png");
                    assertThat(meta.getContentType()).isEqualTo("image/png");
                    assertThat(meta.getSize()).isEqualTo(5L);
                    assertThat(meta.getUploaderId()).isEqualTo(7L);
                });
    }

    @Test
    void 빈_파일은_거절한다() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileService.upload(empty, "profile", 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }

    @Test
    void 확장자가_없으면_확장자_없는_key를_만든다() {
        MockMultipartFile noExt = new MockMultipartFile(
                "file", "noext", "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));

        StoredFile stored = fileService.upload(noExt, "misc", null);

        assertThat(stored.storageKey()).doesNotContain(".");
    }

    @Test
    void 삭제하면_스토리지와_메타데이터에서_모두_사라진다() {
        StoredFile stored = fileService.upload(pngFile(), "profile", 7L);

        fileService.delete(stored.storageKey());

        assertThat(fileStorage.stored).doesNotContainKey(stored.storageKey());
        assertThat(fileMetadataRepository.findByStorageKey(stored.storageKey())).isEmpty();
    }

    @Test
    void 모르는_key를_삭제하면_FILE_NOT_FOUND() {
        assertThatThrownBy(() -> fileService.delete("profile/2026/07/14/unknown.png"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    /** 디스크/AWS 없이 FileService 를 검증하기 위한 인메모리 저장소. */
    static class FakeFileStorage implements FileStorage {

        final Map<String, byte[]> stored = new HashMap<>();

        @Override
        public String upload(String key, InputStream content, long size, String contentType) {
            try {
                stored.put(key, content.readAllBytes());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return key;
        }

        @Override
        public void delete(String key) {
            stored.remove(key);
        }

        @Override
        public String getUrl(String key) {
            return "http://fake/" + key;
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.FileServiceTest"
```

Expected: 컴파일 실패 — `FileService`, `StoredFile`이 없다.

- [ ] **Step 3: 최소 구현**

`StoredFile.java`:

```java
package com.back.global.storage;

/** 업로드 결과. 도메인 엔티티는 {@code storageKey}를 보관하고, {@code url}은 응답에 쓴다. */
public record StoredFile(Long id, String storageKey, String url) {
}
```

`FileService.java`:

```java
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
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.storage.FileServiceTest"
```

Expected: PASS (5개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/global/storage/FileService.java BE/src/main/java/com/back/global/storage/StoredFile.java BE/src/test/java/com/back/global/storage/FileServiceTest.java
git commit -m "feat: FileService 추가 - key 생성·저장·메타데이터 영속화 조합"
```

---

## Task 7: 로컬 파일 서빙 (`/files/**`) + 보안 규칙

`LocalFileStorage`가 저장한 파일을 브라우저가 볼 수 있어야 FE 연동 검증이 된다.

**보안 규칙 변경:** 현재 `SecurityConfig`는 `/api/auth/**`만 permit이고 나머지는 전부 `authenticated()`라 `/files/**`가 401로 막힌다. permit을 추가한다.

**Files:**
- Create: `BE/src/main/java/com/back/global/config/LocalFileServingConfig.java`
- Modify: `BE/src/main/java/com/back/global/security/SecurityConfig.java:36`
- Test: `BE/src/test/java/com/back/global/security/SecurityConfigTest.java` (기존 파일에 추가)

**Interfaces:**
- Consumes: `StorageProperties`(Task 2)
- Produces: `/files/**` 경로가 인증 없이 접근 가능해진다

- [ ] **Step 1: 실패하는 테스트 작성**

`SecurityConfigTest.java`에 추가한다.

```java
    @Test
    void 파일_경로는_토큰_없이_접근_가능하다() throws Exception {
        // 이 슬라이스에는 정적 리소스 핸들러가 없으므로 404가 정상이다.
        // 핵심은 401(인증 요구)이 아니라는 것 — 인가 규칙에서 permit 되었는지만 검증한다.
        mockMvc.perform(get("/files/profile/2026/07/14/abc.png"))
                .andExpect(status().isNotFound());
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.security.SecurityConfigTest"
```

Expected: FAIL — 401 Unauthorized를 받는다 (404를 기대했으나 401).

- [ ] **Step 3: 최소 구현**

`SecurityConfig.java`의 `authorizeHttpRequests` 블록을 수정한다. `/files/**` 한 줄을 추가한다.

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/files/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
```

`LocalFileServingConfig.java`:

```java
package com.back.global.config;

import com.back.global.storage.StorageProperties;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * LocalFileStorage 가 저장한 파일을 {@code /files/**} 로 서빙한다.
 * {@code storage.type=s3}일 때는 파일이 서버를 거치지 않으므로 등록하지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileServingConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path rootDir = Paths.get(properties.local().rootDir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(rootDir.toUri().toString());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd BE && ./gradlew test --tests "com.back.global.security.SecurityConfigTest"
```

Expected: PASS (6개 테스트 — 기존 5개 + 신규 1개)

- [ ] **Step 5: 전체 빌드 확인**

```bash
cd BE && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL. 전체 테스트가 통과해야 한다.

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/global/config/LocalFileServingConfig.java BE/src/main/java/com/back/global/security/SecurityConfig.java BE/src/test/java/com/back/global/security/SecurityConfigTest.java
git commit -m "feat: 로컬 파일 서빙(/files/**) 및 보안 permit 규칙 추가"
```

---

## Task 8: 문서 반영

`docs/`가 정본이다. 코드가 바뀌었으니 문서를 맞춘다. **문서 충돌 해소(설계 §2-4)를 반드시 반영한다.**

**Files:**
- Modify: `docs/DOMAIN-COMMON-STATUTE.md` (§7 파일 업로드)
- Modify: `docs/ARCHITECTURE-STATUTE.md` (§3 파일 저장 규칙)
- Modify: `docs/CONTEXT.md`
- Modify: `docs/TODO-READY.md` (FileStorage 항목 제거)
- Modify: `docs/TODO-DONE.md` (완료 기록 추가)
- Modify: `docs/AI-ACTION-LOGS.md`

- [ ] **Step 1: `DOMAIN-COMMON-STATUTE.md` §7 개정**

기존 §7 본문(2줄)을 아래로 교체한다. 공용 `FileMetadata` 결정을 반영한다.

```markdown
## 7. 파일 업로드

* 파일 업로드가 필요한 도메인은 `com.back.global.storage.FileService`를 통해 저장한다.
  `FileStorage`(바이트 저장소)를 직접 호출하지 않는다.
* `FileStorage`는 DB를 모른다. key 생성과 메타데이터 영속화는 `FileService`의 책임이다.
* 모든 업로드는 공용 `FileMetadata` 엔티티(`storageKey`/`originalName`/`contentType`/`size`/`uploaderId`)에 기록한다.
* 도메인 엔티티는 `storageKey`를 보관한다. 접근 URL은 `FileService.getUrl(key)`로 만든다(저장하지 않는다).
* key 형식: `{디렉터리}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}`. 원본 파일명은 key에 넣지 않는다.
* 허용 contentType·크기 제한 같은 정책은 각 도메인이 정한다. `FileService`는 빈 파일만 거절한다.
```

- [ ] **Step 2: `ARCHITECTURE-STATUTE.md` §3 개정**

기존 §3의 마지막 두 항목을 아래로 교체·보강한다.

```markdown
## 3. 파일 저장 규칙

* 파일 저장 접근은 반드시 `FileStorage` 인터페이스를 통해서만 한다. 도메인 서비스가 S3 SDK나 물리 경로를 직접 다루지 않는다.
* 저장 결과는 물리 경로가 아니라 **논리 key + 접근 URL**로 다룬다.
* 구현체는 둘이다. `storage.type` 설정으로 하나만 활성화된다.
  * `local` (기본값) : `LocalFileStorage` — 로컬 디스크에 저장하고 `/files/**`로 서빙한다.
  * `s3` : `S3FileStorage` — AWS SDK v2 사용.
* 접근 URL은 `base-url + key`로 만든다. 이 `base-url`이 CloudFront/R2 등으로 갈아타는 **단일 교체 지점**이다.
* 업로드는 서버 경유 방식으로 시작한다. (추후 필요 시 Presigned URL 직접 업로드로 확장 가능)
* 업로드 파일 메타데이터는 공용 `FileMetadata` 엔티티에 저장한다. (`DOMAIN-COMMON-STATUTE §7`)
* S3 자격증명·버킷명은 환경변수로 분리하며 저장소에 커밋하지 않는다.
```

- [ ] **Step 3: `CONTEXT.md` 갱신**

`## 현재 상태`의 단계 줄을 아래로 교체한다.

```markdown
* 단계: BE 전역 기반 + 보안 골격 + MEMBER 자체/카카오 로그인 + 파일 저장 기반(FileStorage) 완료. 다음은 MEMBER 프로필/이미지 또는 구글 소셜 확장 또는 FE 초기화.
```

`## 주의` 섹션 맨 끝에 아래 두 줄을 추가한다.

```markdown
* 파일 저장: `FileStorage`(바이트) + `FileService`(key생성·메타데이터). 도메인은 `FileService`만 사용. `storage.type`=local(기본)/s3. 로컬은 `/files/**`로 서빙(SecurityConfig permit).
* S3 키(`S3_BUCKET`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)는 env 주입·커밋 금지. **실제 S3 연동은 아직 미검증**(자격증명 미발급, 자동 테스트는 S3Client 목).
```

- [ ] **Step 4: `TODO-READY.md` / `TODO-DONE.md` 갱신**

`TODO-READY.md`에서 FileStorage 줄을 제거한다. 남는 항목은 FE 초기화뿐이다.

`TODO-DONE.md` 맨 위(`---` 아래)에 추가한다.

```markdown
* [x] (2026-07-14) 파일 저장 기반(FileStorage) 구성 (TDD)
  * `global.storage`: `FileStorage`(인터페이스) / `LocalFileStorage`(기본) / `S3FileStorage`(AWS SDK v2) / `StorageProperties`
  * `FileService` — key 생성(`{디렉터리}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}`) + 저장 + `FileMetadata` 영속화를 한 트랜잭션으로 조합
  * `FileStorage`는 DB를 모른다(책임 분리). 도메인은 `FileService`만 사용
  * 접근 URL = `base-url + key` → CloudFront/R2 전환 시 설정 한 줄만 교체
  * 에러코드 추가: `INVALID_FILE`(400-02), `FILE_NOT_FOUND`(404-01), `FILE_UPLOAD_FAILED`(500-02). `BusinessException`에 cause 생성자 추가
  * `SecurityConfig`에 `/files/**` permit 추가(로컬 파일 서빙)
  * 문서 충돌 해소: 메타데이터는 공용 `FileMetadata` 테이블로 결정 → `DOMAIN-COMMON-STATUTE §7` 개정
  * 범위 밖: HTTP 업로드 엔드포인트(사용처인 MEMBER 프로필 이미지에서 구현), 실제 S3 연동 검증(자격증명 미발급), Presigned URL
```

- [ ] **Step 5: `AI-ACTION-LOGS.md` 기록 추가**

파일 맨 위 최신 항목 자리에 한 줄 추가한다 (기존 형식을 따른다. 최대 100개 유지).

```markdown
* 2026-07-14 — 파일 저장 기반(FileStorage/FileService/FileMetadata) TDD 구현. 로컬·S3 이중 구현체, 에러코드 3종, `/files/**` permit. 실제 S3 연동은 자격증명 발급 후 수동 검증 예정.
```

- [ ] **Step 6: 최종 전체 빌드**

```bash
cd BE && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add docs/
git commit -m "docs: 파일 저장 기반 구현 반영 및 메타데이터 구조 충돌 해소"
```

---

## 완료 후 남는 것

- **실제 S3 검증 (수동)** — AWS 버킷 생성 + 키 발급 후 `STORAGE_TYPE=s3`로 띄워 업로드/삭제/조회를 확인하고 결과를 `AI-ACTION-LOGS.md`에 남긴다. 자동 테스트에는 넣지 않는다.
- **MEMBER 프로필 이미지 업로드** — 이 저장 계층의 첫 사용처. HTTP 엔드포인트는 여기서 만든다.
- 고아 파일 정리(GC), CloudFront/R2 요금 최적화는 후속 과제.
