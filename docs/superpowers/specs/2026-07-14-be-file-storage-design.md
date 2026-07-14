# BE 파일 저장 기반(FileStorage) 설계

> 작성일: 2026-07-14
> 상태: 설계 승인 완료 → 구현 계획(writing-plans) 전환
> 관련 문서: `docs/ARCHITECTURE-STATUTE.md §2~3`, `docs/DOMAIN-COMMON-STATUTE.md §7`
> 선행 완료: BE 전역 기반(2026-07-05), 보안 골격(2026-07-11), MEMBER 인증(2026-07-12~13)

---

## 1. 목표와 범위

### 목표

파일 저장을 `FileStorage` 인터페이스로 추상화하고, 로컬 디스크 구현체와 S3 구현체를 함께
제공한다. 도메인 서비스가 S3 SDK나 물리 경로를 직접 다루지 않게 하고, 저장 백엔드(S3 →
CloudFront/R2 등)를 나중에 설정만으로 갈아탈 수 있는 여지를 남긴다.

후속 작업인 "MEMBER 프로필 이미지 업로드"가 이 저장 계층 위에 올라간다.

### 범위 (In)

- `com.back.global.storage.FileStorage` 인터페이스
- `LocalFileStorage` (로컬 디스크 구현체, 기본값)
- `S3FileStorage` (AWS SDK v2 구현체, 설정으로 활성화)
- `FileService` — key 생성 + 저장 + 메타데이터 영속화를 조합하는 파사드
- `FileMetadata` 엔티티 + `FileMetadataRepository`
- `StorageProperties` — `storage.*` 설정 바인딩 (자격증명은 환경변수 주입)
- 파일 관련 `ErrorCode` 3종 추가
- `SecurityConfig`에 로컬 파일 서빙 경로(`/files/**`) permit 추가
- `build.gradle.kts`에 AWS SDK v2 의존성 추가

### 범위 밖 (Out)

- **HTTP 업로드 엔드포인트** — 범용 `POST /api/files`를 만들지 않는다. 엔드포인트는 실제
  사용처인 MEMBER 프로필 이미지 작업에서 그 도메인에 맞게 만든다. 쓰는 곳 없는 범용 API를
  미리 만들면 권한·검증 규칙을 근거 없이 추측하게 된다.
- **실제 S3 버킷 연동 검증** — AWS 자격증명이 아직 없다. `S3FileStorage`는 코드로 작성하되
  자동 테스트는 `S3Client` 목으로 요청 조립만 검증하고, 실제 호출은 키 발급 후 수동 검증한다.
  (2026-07-13 카카오 `KakaoOAuthClient`와 동일한 전략)
- **Presigned URL 직접 업로드** — 지금은 서버 경유 업로드만. 인터페이스에 메서드를 추가하면
  확장 가능하도록 구조만 열어둔다.
- **파일 정리(GC)·수명주기 정책** — 고아 파일 정리는 후속 과제.
- **이미지 리사이징/썸네일** — 별도 과제.

---

## 2. 핵심 결정

### 2-1. `FileStorage`는 DB를 모른다 (책임 분리)

`FileStorage` 구현체가 메타데이터까지 저장하면, 인터페이스 하나에 "외부 스토리지 I/O"와
"DB 영속화"라는 성격이 다른 두 책임이 붙는다. 구현체를 추가할 때마다 메타데이터 저장 로직이
중복되고, 순수 Fake로 대체하기도 어려워진다.

따라서 계층을 나눈다.

| 계층 | 책임 | DB 의존 |
|---|---|---|
| `FileStorage` | 바이트를 key에 쓰고, 지우고, URL을 만든다 | 없음 |
| `FileService` | key 생성 → `FileStorage` 호출 → `FileMetadata` 저장 | 있음 |
| 도메인 서비스 | `FileService`만 호출 | — |

이 구조는 요금 측면에서도 이점이 있다. 저장 백엔드를 바꿀 때(R2 등) 구현체 하나만 추가하면
되고, CDN을 붙일 때는 `getUrl()`의 base-url 설정 한 줄만 바꾸면 된다. 세 계층 중 어느 것도
S3 API 호출 횟수를 바꾸지 않으므로 **구조 선택 자체는 AWS 요금에 영향이 없다.** 요금을
가르는 건 조회 트래픽(아웃바운드)이며, 그 최적화 지점을 한 곳(`getUrl`)에 모아두는 것이
이 설계의 목적이다.

### 2-2. key는 `FileService`가 만든다

`FileStorage`는 key를 생성하지 않고 인자로 받는다. 구현체가 각자 key를 만들면 Local과 S3의
key 규칙이 갈라져서, 백엔드를 바꾸는 순간 기존 URL이 전부 깨진다.

**key 형식**: `{디렉터리}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}`

예: `profile/2026/07/14/9f3c1a2b-....png`

- 날짜를 접두사로 넣는 이유: 나중에 오래된 파일을 접두사로 골라내 수명주기 정책을 걸 수 있다.
- **원본 파일명은 key에 쓰지 않는다.** 한글·공백·특수문자가 URL로 새어나가는 것을 막는다.
  원본명은 `FileMetadata.originalName`에만 보관한다.
- 확장자는 원본 파일명에서 추출하되, 없으면 확장자 없이 저장한다.

### 2-3. URL은 교체 가능한 base-url + key

`getUrl(key)` = `base-url + "/" + key`.

이 `base-url`이 나중에 CloudFront 도메인이나 Cloudflare R2 도메인으로 바뀌는 단일 교체
지점이다. Presigned 조회 URL은 CDN 캐싱이 어려워 아웃바운드 요금이 그대로 나오므로,
공개 접근이 전제인 프로필·피드 이미지에는 쓰지 않는다.

### 2-4. 문서 충돌 해소 — 메타데이터는 공용 테이블

기존 문서 두 곳이 충돌했다.

- `ARCHITECTURE-STATUTE §3`: "업로드 파일 메타데이터(원본명, 크기, contentType, key)는 DB에 저장한다"
- `DOMAIN-COMMON-STATUTE §7`: "저장 후 얻은 논리 key와 접근 URL을 **도메인 엔티티에** 보관한다"

전자는 공용 테이블을, 후자는 도메인 엔티티 필드를 가리켜 서로 다른 구조다.

**결정: 공용 `FileMetadata` 엔티티를 도입한다.** 모든 업로드를 한 테이블에 기록하면 고아
파일 정리와 업로드 감사가 가능해진다. 도메인 엔티티는 `FileMetadata`를 참조하거나
`storageKey`를 보관한다.

→ 이에 맞춰 `DOMAIN-COMMON-STATUTE §7`을 개정한다.

---

## 3. 구성요소

패키지: `com.back.global.storage` (S3 클라이언트 빈만 `global.config`)

### 3-1. `FileStorage` (인터페이스)

```java
public interface FileStorage {
    /** 주어진 key 위치에 내용을 저장하고 저장된 key를 반환한다. */
    String upload(String key, InputStream content, long size, String contentType);

    /** key에 해당하는 파일을 삭제한다. 없으면 조용히 무시한다(멱등). */
    void delete(String key);

    /** key의 공개 접근 URL을 만든다. */
    String getUrl(String key);
}
```

- `delete`를 멱등으로 두는 이유: 재시도·중복 삭제가 예외를 던지면 호출 측이 매번 존재 확인을
  해야 한다. 삭제의 목적은 "없는 상태"이므로 이미 없으면 성공으로 본다.

### 3-2. `LocalFileStorage` (기본 구현체)

- `storage.local.root-dir` 아래에 key를 상대 경로로 삼아 실제 파일을 쓴다.
- 상위 디렉터리는 필요 시 생성한다.
- **경로 탈출 방어**: 정규화한 최종 경로가 `root-dir` 밖을 가리키면 `INVALID_FILE`로 거절한다.
  (key에 `../`가 섞여 들어오는 경우)
- `@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)`
  → 기본값이므로 S3 키 없이도 앱이 뜬다.

### 3-3. `S3FileStorage`

- AWS SDK v2 `S3Client.putObject` / `deleteObject`.
- `@ConditionalOnProperty(name = "storage.type", havingValue = "s3")`
- `S3Client` 빈은 `global.config.StorageConfig`에서 같은 조건으로 생성한다.
- SDK 예외(`SdkException`)는 `FILE_UPLOAD_FAILED`로 감싸 밖으로 새지 않게 한다.

### 3-4. `FileService`

```java
StoredFile upload(MultipartFile file, String directory);  // 저장 + 메타데이터 기록
void       delete(String storageKey);                     // 스토리지 + 메타데이터 삭제
```

- `StoredFile`은 `(Long id, String storageKey, String url)` record.
- 빈 파일(`isEmpty()`)은 `INVALID_FILE`로 거절한다.
- **`delete`의 두 계층은 동작이 다르다.** `FileService.delete`는 메타데이터에 없는 key를 받으면
  `FILE_NOT_FOUND`를 던진다 — 호출 측이 모르는 key를 넘긴 것이므로 알려야 한다. 반면 그 아래
  `FileStorage.delete`는 메타데이터가 있는데 물리 파일만 없는 경우(수동 삭제, 이전 실패 등)에도
  예외 없이 진행한다. 즉 **"key를 아는가"는 `FileService`가 따지고, "물리 파일이 실제로 있는가"는
  따지지 않는다.** 삭제의 목적은 없는 상태를 만드는 것이기 때문이다.
- 허용 contentType·최대 크기 같은 **도메인별 정책은 검증하지 않는다.** 프로필 이미지가
  이미지 타입만 받는지는 MEMBER 도메인이 판단할 일이다. 전역 상한은 Spring의
  `spring.servlet.multipart.max-file-size`로 건다.
- `@Transactional` — 스토리지 저장과 메타데이터 기록을 한 경계로 묶는다.

### 3-5. `FileMetadata` (엔티티)

`BaseEntity` 상속 (`createdAt`/`updatedAt` 자동).

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `storageKey` | String | unique, non-null |
| `originalName` | String | non-null |
| `contentType` | String | nullable (브라우저가 안 보낼 수 있음) |
| `size` | long | non-null |
| `uploaderId` | Long | **nullable** |

`uploaderId`를 `Member` 연관관계가 아닌 원시 Long으로 두는 이유: `global`이 `domain.member`를
직접 의존하면 의존 방향이 뒤집힌다 (`global` ← `domain`이어야 한다). 느슨한 참조로 둔다.

---

## 4. 설정

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
    base-url: ${S3_BASE_URL:}          # ← CDN(CloudFront/R2) 교체 지점
```

- `StorageProperties` (`@ConfigurationProperties("storage")`)로 바인딩한다.
- **자격증명은 절대 커밋하지 않는다.** JWT·카카오 키와 동일한 env 주입 원칙.
- `./uploads`는 `.gitignore`에 추가한다.

### 로컬 파일 서빙

`LocalFileStorage`가 쓴 파일을 브라우저가 볼 수 있어야 FE 연동 검증이 된다.

- `WebMvcConfigurer`로 `/files/**` → `file:${storage.local.root-dir}/` 리소스 핸들러 등록.
- **`SecurityConfig` 변경**: 현재 인가 규칙이 `/api/auth/**`만 permit이고 나머지는 전부
  `authenticated()`라 `/files/**`가 401로 막힌다. `/files/**`를 `permitAll`에 추가한다.
- 이 핸들러·permit은 `storage.type=local`일 때만 유효하다. S3 사용 시에는 파일이 서버를
  거치지 않으므로 무해하다.

---

## 5. 에러 코드

전역 `ErrorCode` enum에 추가한다. (기존 번호와 충돌 없음)

| 코드 | resultCode | HTTP | 사유 |
|---|---|---|---|
| `INVALID_FILE` | 400-02 | 400 | 빈 파일, 잘못된 key(경로 탈출 등) |
| `FILE_NOT_FOUND` | 404-01 | 404 | `FileService`가 메타데이터에서 찾지 못한 key (§3-4 참고) |
| `FILE_UPLOAD_FAILED` | 500-02 | 500 | I/O 실패, S3 SDK 예외 |

구현체의 `IOException`·`SdkException`은 밖으로 새지 않게 `FILE_UPLOAD_FAILED`를 담은
`BusinessException`으로 감싼다. 원인 예외는 `cause`로 보존한다.

---

## 6. 데이터 흐름

```
도메인 서비스
   │  fileService.upload(multipartFile, "profile")
   ▼
FileService  ─ 빈 파일 검증 (INVALID_FILE)
   │         ─ key 생성: profile/2026/07/14/{UUID}.png
   │
   ├─► FileStorage.upload(key, stream, size, contentType)   ← Local 또는 S3
   │
   ├─► FileMetadataRepository.save(메타데이터)
   │
   └─► StoredFile(id, storageKey, url) 반환
              │
              └─ url = FileStorage.getUrl(key) = base-url + "/" + key
```

---

## 7. 테스트 전략 (TDD)

| 대상 | 방식 |
|---|---|
| `LocalFileStorage` | `@TempDir`로 실제 디스크에 쓰고/읽고/지우는 것까지 검증. 경로 탈출 key 거절 검증 |
| `S3FileStorage` | `S3Client`를 Mockito 목으로 주입. **실제 AWS 호출 없음.** `PutObjectRequest`의 bucket·key·contentType이 올바른지, SDK 예외가 `FILE_UPLOAD_FAILED`로 변환되는지 검증 |
| `FileService` | Fake `FileStorage` 주입. key 생성 규칙(디렉터리/날짜/UUID/확장자), 메타데이터 저장 내용, 빈 파일 거절 검증 |
| `FileMetadataRepository` | `@DataJpaTest` (H2). `storageKey` unique 제약 검증 |
| `StorageProperties` | 바인딩 및 기본값 검증 |
| `SecurityConfig` | `/files/**`가 인증 없이 접근 가능한지 회귀 검증 |

테스트 프로파일은 `storage.type=local` + 임시 디렉터리를 쓴다.

**실제 S3 검증은 자동 테스트에 포함하지 않는다.** AWS 키 발급 후 수동으로 확인하고 그 결과를
`docs/AI-ACTION-LOGS.md`에 남긴다.

---

## 8. 문서 반영

구현 완료 시 함께 갱신한다.

- `docs/DOMAIN-COMMON-STATUTE.md §7` — 공용 `FileMetadata` 구조로 개정 (§2-4 결정)
- `docs/ARCHITECTURE-STATUTE.md §3` — 로컬/S3 이중 구현체, base-url 교체 지점 명시
- `docs/CONTEXT.md` — 현재 상태 및 주의사항
- `docs/TODO-READY.md` → `docs/TODO-DONE.md`
- `docs/AI-ACTION-LOGS.md`

---

## 9. 후속 작업

- MEMBER 프로필(악기/장르/자기소개) + 프로필 이미지 업로드 — 이 저장 계층의 첫 사용처
- AWS S3 버킷 생성 + 자격증명 발급 → `S3FileStorage` 실제 검증
- 요금 최적화: CloudFront 또는 Cloudflare R2 검토 (아웃바운드 트래픽이 실제로 발생한 이후)
- 고아 파일 정리(GC) 정책
