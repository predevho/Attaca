# BE 런타임 DB(MySQL) + MEMBER 프로필/이미지 설계

> 작성일: 2026-07-15
> 상태: 설계 승인 완료(A안) → 구현 계획(writing-plans) 전환
> 관련 문서: `docs/ARCHITECTURE-STATUTE.md §1`, `docs/DOMAIN-MEMBER-STATUTE.md §2·§5`, `docs/DOMAIN-COMMON-STATUTE.md §7`
> 선행 완료: MEMBER 인증(2026-07-12~13), 파일 저장 기반 FileStorage/FileService(2026-07-14)

---

## 1. 목표와 범위

### 목표

두 가지를 한 흐름으로 해결한다.

1. **런타임 DB 구성**: 현재 H2가 `testRuntimeOnly`뿐이라 `bootRun`으로 서버를 띄울 수 없다.
   Docker Compose 기반 MySQL을 붙여 로컬 서버 기동을 가능하게 한다. (FE 연동·수동 검증의 선행 조건)
2. **MEMBER 프로필**: 악기/장르/자기소개 + 프로필 이미지 업로드. 파일 저장 계층(`FileService`)의
   첫 실사용처다.

### 범위 (In)

- `docker-compose.yml`(레포 루트) — MySQL 8.4 로컬 개발용
- `application.yaml`에 `spring.datasource`(env 기본값) + `mysql-connector-j`(runtimeOnly)
- 테스트 프로파일 분리: `src/test/resources/application-test.yaml`(H2) + `@SpringBootTest`류에 `@ActiveProfiles("test")`
- `MemberProfile` 엔티티(1:1, lazy upsert) + `Instrument`/`Genre` enum
- API 4개: 내 프로필 GET/PUT, 프로필 이미지 PUT, 프로필 선택지 GET
- Bean Validation 도입(`spring-boot-starter-validation`) + 전역 예외 처리기 보강 3건
- `ErrorCode` 추가: `MEMBER_NOT_FOUND`(404-03)

### 범위 밖 (Out)

- **다른 회원 프로필 조회** — "공개 범위" 정책이 없다. FEED/VERIFIED-PERFORMER 등 실제 소비처가
  생길 때 그 요구에 맞춰 추가한다.
- **프로필 이미지 삭제 API** — 교체만 지원. 삭제는 FE 요구가 생기면.
- **이미지 리사이징/썸네일**, Presigned URL.
- **운영 `ddl-auto: validate` 전환 + 마이그레이션 도구(Flyway 등)** — 배포 단계에서 결정.
- 실제 S3 연동 검증(기존 BACKLOG 유지).

---

## 2. ① 런타임 DB 구성

### 2-1. Docker Compose

레포 루트 `docker-compose.yml`:

```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: attaca-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_DATABASE: attaca
      MYSQL_USER: attaca
      MYSQL_PASSWORD: attaca-local     # 로컬 개발 전용. 운영 자격증명은 env로 별도 주입
      MYSQL_ROOT_PASSWORD: root-local
    volumes:
      - mysql-data:/var/lib/mysql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

volumes:
  mysql-data:
```

- 계정/비밀번호는 **로컬 개발 전용 기본값**이다. "자격증명 커밋 금지" 원칙은 운영 자격증명에
  대한 것이며, 이 값들은 localhost 밖에서 쓸 수 없다. `application.yaml` 기본값과 일치시킨다.
- 사용법: `docker compose up -d` → `cd BE && ./gradlew bootRun`.

### 2-2. 데이터소스 설정

`application.yaml`의 `spring:` 블록에 추가:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/attaca}
    username: ${DB_USERNAME:attaca}
    password: ${DB_PASSWORD:attaca-local}
  jpa:
    hibernate:
      ddl-auto: ${DDL_AUTO:update}   # 개발 단계 편의. 운영 전환 시 validate+마이그레이션 도구로 재결정
    open-in-view: false
```

- `mysql-connector-j`는 `runtimeOnly` 의존성.
- `open-in-view: false`를 명시한다(기본 true는 뷰 렌더링용 세션 유지 — API 서버에 불필요하고
  경고 로그만 남긴다).

### 2-3. 테스트 프로파일 분리 (함정 해소)

main `application.yaml`에 MySQL URL이 생기는 순간, 지금까지 "데이터소스 설정 없음 → H2 자동
구성"으로 돌던 `@SpringBootTest`들이 MySQL 접속을 시도하다 실패한다. (`@DataJpaTest`는 임베디드
교체가 기본이라 무관)

- `BE/src/test/resources/application-test.yaml` 생성:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:attaca-test;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
```

- 모든 `@SpringBootTest` 클래스에 `@ActiveProfiles("test")`를 부착한다. (구현 시 grep으로 전수
  확인. 최소: `AttacaApplicationTests`, `LocalFileServingConfigTest`, MEMBER 컨트롤러 통합 테스트류)
- 프로파일 파일은 main `application.yaml` 위에 **덮어쓰기로 병합**되므로 jwt/oauth/storage
  기본값은 그대로 유지된다. (test 리소스에 같은 이름 `application.yaml`을 두면 main이 통째로
  가려지므로 금지)

### 2-4. 검증

- 자동: 전체 `clean build`(H2 프로파일)가 계속 통과해야 한다.
- 수동: `docker compose up -d` → `bootRun` → `POST /api/auth/signup` 정상 응답과 MySQL에
  행 생성 확인 → 결과를 `AI-ACTION-LOGS.md`에 기록.

---

## 3. ② MemberProfile 모델링 (A안 확정)

### 3-1. 엔티티

`com.back.domain.member.entity.MemberProfile` — `BaseEntity` 상속, 기존 엔티티 패턴
(`@NoArgsConstructor(PROTECTED)` + private 생성자 + static 팩토리) 준수.

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `member` | Member | `@OneToOne(fetch = LAZY)`, `member_id` unique·non-null. **단방향** — Member 쪽엔 필드를 추가하지 않는다 |
| `instruments` | `Set<Instrument>` | `@ElementCollection(fetch = LAZY)` + `@Enumerated(STRING)`, 테이블 `member_profile_instrument` |
| `genres` | `Set<Genre>` | 동일 패턴, 테이블 `member_profile_genre` |
| `bio` | String | nullable, 최대 500자(`@Column(length = 500)`) |
| `profileImageKey` | String | nullable. **URL은 저장하지 않는다**(COMMON §7, `FileService.getUrl`로 생성) |

- **생성 시점: lazy upsert.** 가입 시 자동 생성하지 않는다. 첫 `PUT /me/profile` 또는 첫 이미지
  업로드 때 없으면 만든다. GET은 프로필이 없으면 **빈 기본값 응답**(404 아님) — FE가 편집 화면을
  그리기 쉽다.
- 변경 메서드: `updateInfo(Set<Instrument>, Set<Genre>, String bio)` / `changeImage(String newKey)`
  (setter 금지, 의도가 드러나는 메서드).
- 컬렉션을 `Set`으로 두는 이유: 중복 선택 무의미 + `@ElementCollection` 전체 교체 시맨틱과 맞음.

### 3-2. Instrument enum (초안 — 리뷰 시 조정 가능)

`@Getter` + 한글 `label` 필드. `domain.member.entity`.

| 그룹 | 값 |
|---|---|
| 현악 | VIOLIN(바이올린), VIOLA(비올라), CELLO(첼로), DOUBLE_BASS(더블베이스), HARP(하프) |
| 목관 | FLUTE(플루트), OBOE(오보에), CLARINET(클라리넷), BASSOON(바순) |
| 금관 | HORN(호른), TRUMPET(트럼펫), TROMBONE(트롬본), TUBA(튜바) |
| 건반 | PIANO(피아노), ORGAN(오르간) |
| 밴드/대중 | GUITAR(기타), BASS_GUITAR(베이스 기타), DRUMS(드럼) |
| 기타 | PERCUSSION(타악기), VOCAL(보컬), COMPOSITION(작곡), CONDUCTING(지휘), ETC(그 외) |

### 3-3. Genre enum (초안)

CLASSICAL(클래식), JAZZ(재즈), POP(팝), ROCK(록), BAND(밴드), CCM(CCM), MUSICAL(뮤지컬),
OST(OST), KPOP(K-POP), KOREAN_TRADITIONAL(국악), ETC(그 외)

- enum 확장은 값 추가만으로 끝난다(하위호환). 값 삭제/개명은 기존 데이터 마이그레이션이 필요하므로
  주요 결정으로 기록 후 진행.

---

## 4. API

모두 인증 필요(기존 `anyRequest().authenticated()` 그대로 — SecurityConfig 변경 없음).
회원 식별은 JWT principal(`Long memberId`, `@AuthenticationPrincipal`)로 한다.
위치: `com.back.domain.member` (controller `MemberProfileController`, service `MemberProfileService`,
repository `MemberProfileRepository`, dto).

### 4-1. `GET /api/members/me/profile`

응답 `ProfileResponse`:

```json
{ "instruments": ["VIOLIN"], "genres": ["CLASSICAL"], "bio": "...", "profileImageUrl": "http://.../files/profile/..." }
```

- 프로필 미생성 시: `instruments`/`genres` 빈 배열, `bio`/`profileImageUrl` null.
- `profileImageUrl`은 `profileImageKey`가 있을 때만 `FileService.getUrl(key)`로 생성.

### 4-2. `PUT /api/members/me/profile`

요청 `UpdateProfileRequest`:

```json
{ "instruments": ["VIOLIN", "PIANO"], "genres": ["CLASSICAL"], "bio": "안녕하세요" }
```

- **전체 교체(upsert)**. 프로필이 없으면 생성한다. `null` 목록은 빈 목록으로 간주.
- 검증(Bean Validation): `instruments`/`genres` 각 최대 10개(`@Size(max = 10)`),
  `bio` 최대 500자(`@Size(max = 500)`).
- 응답: 갱신된 `ProfileResponse`.

### 4-3. `PUT /api/members/me/profile/image`

- multipart, 파트 이름 `file`.
- **도메인 정책 검증**(COMMON §7 — 정책은 도메인 책임): `contentType`이 `image/`로 시작하지
  않으면(또는 null) `INVALID_FILE`(400-02) + 메시지 "이미지 파일만 업로드할 수 있습니다."
  (기존 코드 재사용, 신규 에러코드 없음. 크기 상한은 전역 multipart 10MB가 담당)
- 흐름: `FileService.upload(file, "profile", memberId)` 성공 → 프로필 upsert로 key 교체 →
  **그 후** 옛 key가 있으면 `FileService.delete(oldKey)`. (새 파일 저장이 확정된 뒤에만 옛 파일
  제거 — 실패 시에도 이미지 유실 없음)
- 응답 `ProfileImageResponse`: `{ "profileImageUrl": "..." }`.

### 4-4. `GET /api/members/profile-options`

FE가 선택지를 하드코딩하지 않도록 enum 목록 제공.

```json
{ "instruments": [{"code": "VIOLIN", "label": "바이올린"}, ...], "genres": [{"code": "CLASSICAL", "label": "클래식"}, ...] }
```

### 4-5. 회원 부재 처리

JWT는 유효하지만 해당 회원이 DB에 없는 경우(탈퇴 등 엣지) `MEMBER_NOT_FOUND`(404-03,
"회원을 찾을 수 없습니다.") 신설해 응답. upsert 경로에서 `memberRepository.findById` 실패 시 사용.

---

## 5. 전역 예외 처리기 보강 (이 작업의 선행 결함 수정)

현재 아래 세 예외가 전부 catch-all에 걸려 **500**으로 응답된다. 클라이언트 실수이므로 400이
맞다. `GlobalExceptionHandler`에 핸들러 3건을 추가하고 모두 `INVALID_INPUT_VALUE`(400-01)로
매핑한다.

| 예외 | 발생 상황 |
|---|---|
| `MethodArgumentNotValidException` | `@Valid` 검증 실패 (bio 501자 등) |
| `HttpMessageNotReadableException` | 본문 파싱 실패 — **enum 오타(`"VIOLINN"`) 포함** |
| `MissingServletRequestPartException` | multipart `file` 파트 누락 |

- 검증 실패 메시지는 첫 필드 오류의 메시지를 `ApiResponse.error(errorCode, message)`로 노출.
- `spring-boot-starter-validation` 의존성 추가(현재 없음).

---

## 6. 테스트 전략 (TDD)

| 대상 | 방식 |
|---|---|
| `MemberProfile` 엔티티 | 팩토리·변경 메서드 단위 검증 |
| `MemberProfileRepository` | `@DataJpaTest`(+`JpaAuditingConfig` import), `findByMemberId`, member unique 제약 |
| `MemberProfileService` | `@DataJpaTest` + 실제 `FileService` + `FakeFileStorage` 주입(기존 `FileServiceTest` 패턴). upsert 생성/갱신, 이미지 교체 시 옛 key 삭제, 비이미지 거절, 회원 부재 |
| `MemberProfileController` | 기존 MEMBER 컨트롤러 테스트 패턴 준수(구현 시 확인). 4개 엔드포인트 + 검증 실패 400-01 + 인증 없음 401 |
| `GlobalExceptionHandler` | 신규 핸들러 3건 각각 400-01 매핑 검증 |
| enum | label 존재·목록 API 매핑 검증 |
| 프로파일 분리 회귀 | 전체 `clean build`가 H2로 통과(자동). MySQL 실기동은 수동 검증 후 로그 기록 |

---

## 7. 문서 반영 (구현 완료 시)

- `DOMAIN-MEMBER-STATUTE` §2(MemberProfile 구현 반영·enum 확정), §5(API 4개 확정 기재)
- `ARCHITECTURE-STATUTE` §1(datasource·docker-compose 언급)
- `CONTEXT.md` — bootRun 가능해짐(docker compose 사용법 한 줄), 테스트 프로파일 주의, 낡은 "bootRun 불가" 항목 교체
- `AI-MAJOR-EVENT` — 악기/장르 enum 고정 목록 채택 결정(자유 문자열 대안 대비 표기 통일·추후 필터 대비)
- TODO-READY 2건 → TODO-DONE, `AI-ACTION-LOGS`
- 노션 TODO 보드/스케줄 동기화
