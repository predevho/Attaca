# BE 런타임 DB(MySQL) + MEMBER 프로필/이미지 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Docker Compose 기반 MySQL로 로컬 서버 기동(`bootRun`)을 가능하게 하고, MEMBER 프로필(악기/자기소개/이미지) API 4개를 구현한다.

**Architecture:** 런타임 데이터소스는 main `application.yaml`(MySQL, env 기본값)에, 테스트는 `application-test.yaml`(H2) 프로파일로 분리한다. 프로필은 `MemberProfile` 1:1 단방향 엔티티(lazy upsert)이며, 이미지는 기존 `FileService`를 경유한다(이 계층의 첫 실사용처). Bean Validation을 도입하고 전역 예외 처리기에 400 매핑 3건을 보강한다.

**Tech Stack:** Java 21 / Spring Boot 3.4.5 / Spring Data JPA / MySQL 8.4(Docker Compose) / H2(테스트) / Bean Validation / JUnit 5 + AssertJ

**설계 문서:** `docs/superpowers/specs/2026-07-15-be-runtime-db-member-profile-design.md`

## Global Constraints

- Spring Boot **3.4.5**, Gradle **8.11.1**, JDK **21**. Gradle 9는 Boot 3.4 미지원 — 래퍼를 올리지 말 것.
- 단순 필드 접근자는 Lombok `@Getter`. 엔티티는 `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + `static create(...)` 팩토리. setter 금지.
- 모든 엔티티는 `BaseEntity` 상속.
- `ErrorCode.resultCode`는 `HTTP상태-일련번호` 문자열. 기존 번호와 충돌 금지 (404-01/404-02는 사용 중, **404-03이 신규**).
- 도메인 파일 접근은 `FileService`만 사용(`FileStorage` 직접 호출 금지). URL은 저장하지 않고 `FileService.getUrl(key)`로 생성.
- 커밋 메시지는 한글, `유형: 한글 요약` 형태.
- 각 태스크 끝에서 `BE/gradlew -p BE clean build`가 통과해야 한다. (샌드박스가 CWD를 오염시킬 수 있으므로 **반드시 `-p BE`로 프로젝트 디렉터리를 명시**한다)
- 실제 MySQL 접속은 자동 테스트에 넣지 않는다(자동 테스트는 전부 H2 프로파일).

---

## File Structure

**Task 1 (런타임 DB):**

| 파일 | 작업 |
|---|---|
| `docker-compose.yml` (레포 루트) | 생성 — MySQL 8.4 |
| `BE/build.gradle.kts` | 수정 — `mysql-connector-j` runtimeOnly |
| `BE/src/main/resources/application.yaml` | 수정 — datasource + jpa |
| `BE/src/test/resources/application-test.yaml` | 생성 — H2 + 테스트용 storage 루트 |
| `@SpringBootTest` 4개 클래스 | 수정 — `@ActiveProfiles("test")` |

**Task 2 (검증/예외):** `BE/build.gradle.kts`(validation), `ErrorCode.java`, `GlobalExceptionHandler.java`

**Task 3~5 (프로필, `com.back.domain.member`):**

| 파일 | 책임 |
|---|---|
| `entity/Instrument.java` | 악기 enum 21종(+한글 label) |
| `entity/MemberProfile.java` | 프로필 엔티티(1:1 단방향) |
| `repository/MemberProfileRepository.java` | `findByMemberId` |
| `dto/ProfileResponse.java` / `dto/UpdateProfileRequest.java` / `dto/ProfileImageResponse.java` / `dto/ProfileOptionsResponse.java` | 요청/응답 DTO |
| `service/MemberProfileService.java` | upsert·이미지 교체 로직 |
| `controller/MemberProfileController.java` | API 4개 |

**Task 6 (문서):** `docs/` 반영

---

## Task 1: 런타임 DB 구성 + 테스트 프로파일 분리

main yaml에 MySQL datasource가 생기면 기존 `@SpringBootTest` 4개가 MySQL 접속을 시도하다 깨진다. 이 깨짐을 눈으로 확인한 뒤(RED) 테스트 프로파일로 고친다(GREEN).

**Files:**
- Create: `docker-compose.yml` (레포 루트)
- Modify: `BE/build.gradle.kts`
- Modify: `BE/src/main/resources/application.yaml`
- Create: `BE/src/test/resources/application-test.yaml`
- Modify: `BE/src/test/java/com/back/AttacaApplicationTests.java`
- Modify: `BE/src/test/java/com/back/global/config/LocalFileServingConfigTest.java`
- Modify: `BE/src/test/java/com/back/domain/member/controller/MemberAuthControllerTest.java`
- Modify: `BE/src/test/java/com/back/domain/member/controller/MemberAuthControllerOAuthTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `bootRun` 가능한 런타임 구성. 이후 태스크의 `@SpringBootTest`는 모두 `@ActiveProfiles("test")` 프로파일에서 돈다.

- [ ] **Step 1: 의존성·설정 추가 (의도적 RED 유발)**

`BE/build.gradle.kts`의 `dependencies`에서 `developmentOnly(...)` 줄 아래에 추가:

```kotlin
    runtimeOnly("com.mysql:mysql-connector-j")
```

`BE/src/main/resources/application.yaml`의 `spring:` 블록을 다음으로 교체(기존 `application.name`·`servlet.multipart`는 유지):

```yaml
spring:
  application:
    name: attaca
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/attaca}
    username: ${DB_USERNAME:attaca}
    password: ${DB_PASSWORD:attaca-local}
  jpa:
    hibernate:
      ddl-auto: ${DDL_AUTO:update}   # 개발 단계 편의. 운영 전환 시 validate+마이그레이션 도구로 재결정
    open-in-view: false
```

- [ ] **Step 2: 테스트가 깨지는지 확인 (RED)**

```bash
BE/gradlew -p BE test --tests "com.back.AttacaApplicationTests"
```

Expected: FAIL — MySQL(localhost:3306) 접속 실패로 컨텍스트 로딩 에러. (H2 자동 구성이 datasource 설정에 밀려났음을 확인)

- [ ] **Step 3: 테스트 프로파일 작성 (GREEN)**

`BE/src/test/resources/application-test.yaml` 생성:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:attaca-test;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop

storage:
  local:
    root-dir: build/test-uploads   # 테스트가 실제 ./uploads 를 오염시키지 않도록 격리
```

> 같은 이름의 `application.yaml`을 test 리소스에 두면 main 파일이 통째로 가려져 jwt/oauth/storage 기본값이 사라진다. 반드시 **프로파일 파일**(`application-test.yaml`)로 만들어 main 위에 병합되게 한다.

`@SpringBootTest`가 붙은 4개 클래스(위 Files 목록)에 각각 `@ActiveProfiles("test")`를 추가한다. 예 (`AttacaApplicationTests.java`):

```java
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AttacaApplicationTests {
```

나머지 3개 클래스도 클래스 레벨 애너테이션과 import만 동일하게 추가한다(기존 애너테이션·본문은 그대로).

- [ ] **Step 4: 전체 빌드 통과 확인 (GREEN)**

```bash
BE/gradlew -p BE clean build
```

Expected: BUILD SUCCESSFUL (전체 테스트가 H2로 통과)

- [ ] **Step 5: docker-compose 작성**

레포 루트 `docker-compose.yml` 생성:

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
      MYSQL_PASSWORD: attaca-local     # 로컬 개발 전용 기본값. 운영 자격증명은 env로 별도 주입
      MYSQL_ROOT_PASSWORD: root-local
    volumes:
      - mysql-data:/var/lib/mysql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

volumes:
  mysql-data:
```

- [ ] **Step 6: (수동, 가능한 경우만) MySQL 실기동 검증**

Docker Desktop이 실행 가능하면:

```bash
docker compose up -d
BE/gradlew -p BE bootRun
```

별도 셸에서 `POST http://localhost:8080/api/auth/signup`(body: `{"loginId":"t1","password":"pw12345678","email":"t1@attaca.com","nickname":"테스터"}`)가 200으로 응답하고 MySQL `attaca.member` 테이블에 행이 생기는지 확인 후 서버·컨테이너를 내린다.
**Docker를 쓸 수 없으면 이 스텝을 건너뛰고 보고서에 "수동 검증 미수행"을 명시한다.** (자동 테스트는 영향 없음)

- [ ] **Step 7: 커밋**

```bash
git add docker-compose.yml BE/build.gradle.kts BE/src/main/resources/application.yaml BE/src/test/resources/application-test.yaml BE/src/test/java/com/back/AttacaApplicationTests.java BE/src/test/java/com/back/global/config/LocalFileServingConfigTest.java BE/src/test/java/com/back/domain/member/controller/
git commit -m "feat: 런타임 MySQL 데이터소스 구성 및 테스트 H2 프로파일 분리"
```

---

## Task 2: Bean Validation 도입 + MEMBER_NOT_FOUND + 전역 예외 400 매핑 3건

현재 `@Valid` 실패·본문 파싱 실패·multipart 파트 누락이 전부 catch-all에 걸려 **500**으로 응답된다. 셋 다 클라이언트 실수이므로 `INVALID_INPUT_VALUE`(400-01)로 바로잡는다.

**Files:**
- Modify: `BE/build.gradle.kts`
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java`
- Modify: `BE/src/main/java/com/back/global/exception/GlobalExceptionHandler.java`
- Test: `BE/src/test/java/com/back/global/exception/GlobalExceptionHandlerTest.java` (기존 파일에 추가)
- Test: `BE/src/test/java/com/back/global/exception/ErrorCodeTest.java` (기존 파일에 추가)

**Interfaces:**
- Consumes: 기존 `ApiResponse.error(ErrorCode)` / `error(ErrorCode, String message)`
- Produces:
  - `ErrorCode.MEMBER_NOT_FOUND` (`"404-03"`, `HttpStatus.NOT_FOUND`, "회원을 찾을 수 없습니다.")
  - `@Valid` 실패 / 파싱 불가 본문(enum 오타 포함) / multipart 파트 누락 → 400-01 응답

- [ ] **Step 1: 의존성 추가**

`BE/build.gradle.kts`의 `dependencies`에서 `implementation("org.springframework.boot:spring-boot-starter-web")` 아래에 추가:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-validation")
```

- [ ] **Step 2: 실패하는 테스트 작성**

`ErrorCodeTest.java`에 추가:

```java
    @Test
    void 회원_에러코드는_지정된_resultCode와_상태를_가진다() {
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getResultCode()).isEqualTo("404-03");
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getCode()).isEqualTo("MEMBER_NOT_FOUND");
    }
```

`GlobalExceptionHandlerTest.java`의 `TestController`에 엔드포인트 2개를 추가하고, 테스트 3개를 추가한다:

```java
    // TestController 안에 추가
    @PostMapping("/test/valid")
    public void valid(@Valid @RequestBody ValidBody body) {
    }

    @PostMapping("/test/part")
    public void part(@RequestPart("file") MultipartFile file) {
    }

    record ValidBody(@Size(max = 5) String bio) {
    }
```

```java
    // 테스트 메서드 추가
    @Test
    void validationFailure_mapsTo400InvalidInput() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"여섯글자넘는값\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void malformedBody_mapsTo400InvalidInput() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{이건 json이 아님"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void missingMultipartPart_mapsTo400InvalidInput() throws Exception {
        mockMvc.perform(multipart("/test/part"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }
```

필요 import 추가:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

```bash
BE/gradlew -p BE test --tests "com.back.global.exception.*"
```

Expected: FAIL — `MEMBER_NOT_FOUND` 심볼 없음(컴파일 에러). 컴파일이 통과하는 시점부터는 신규 3개 테스트가 500 응답으로 FAIL.

- [ ] **Step 4: 최소 구현**

`ErrorCode.java`의 `// --- 파일 저장 ---` 그룹 위(MEMBER 그룹 아래)에 추가:

```java
    MEMBER_NOT_FOUND("404-03", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
```

(마지막 상수의 세미콜론 위치가 바뀌지 않도록 그룹 중간에 삽입한다)

`GlobalExceptionHandler.java`의 catch-all(`Exception.class`) 핸들러 **위에** 3개 핸들러를 추가:

```java
    // @Valid 검증 실패. 첫 필드 오류를 메시지로 노출한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(errorCode.getMessage());
        log.warn("MethodArgumentNotValidException: {}", message);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, message));
    }

    // 본문 파싱 실패(JSON 문법 오류, enum에 없는 값 등). 클라이언트 실수이므로 400.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    // multipart 필수 파트 누락.
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        log.warn("MissingServletRequestPartException: part={}", e.getRequestPartName());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }
```

필요 import:

```java
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
BE/gradlew -p BE test --tests "com.back.global.exception.*"
```

Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add BE/build.gradle.kts BE/src/main/java/com/back/global/exception/ BE/src/test/java/com/back/global/exception/
git commit -m "feat: Bean Validation 도입 및 클라이언트 실수 3종 400 매핑, MEMBER_NOT_FOUND 추가"
```

---

## Task 3: Instrument enum + MemberProfile 엔티티 + 리포지토리

**Files:**
- Create: `BE/src/main/java/com/back/domain/member/entity/Instrument.java`
- Create: `BE/src/main/java/com/back/domain/member/entity/MemberProfile.java`
- Create: `BE/src/main/java/com/back/domain/member/repository/MemberProfileRepository.java`
- Test: `BE/src/test/java/com/back/domain/member/entity/MemberProfileTest.java`
- Test: `BE/src/test/java/com/back/domain/member/repository/MemberProfileRepositoryTest.java`

**Interfaces:**
- Consumes: `Member`(기존, `Member.createLocal(loginId, password, email, nickname)` 팩토리), `BaseEntity`
- Produces:
  - `Instrument` enum 21종, `getLabel()` → 한글 표시명
  - `MemberProfile.create(Member member)` → 빈 프로필
  - `MemberProfile.updateInfo(Set<Instrument> instruments, String bio)` / `changeImage(String newKey)`
  - getter: `getId()`, `getMember()`, `getInstruments()`, `getBio()`, `getProfileImageKey()`
  - `MemberProfileRepository.findByMemberId(Long memberId)` → `Optional<MemberProfile>`

- [ ] **Step 1: 실패하는 테스트 작성**

`MemberProfileTest.java`:

```java
package com.back.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberProfileTest {

    private Member member() {
        return Member.createLocal("user1", "encoded-pw", "u1@attaca.com", "유저일");
    }

    @Test
    void 생성_직후에는_빈_프로필이다() {
        MemberProfile profile = MemberProfile.create(member());

        assertThat(profile.getInstruments()).isEmpty();
        assertThat(profile.getBio()).isNull();
        assertThat(profile.getProfileImageKey()).isNull();
    }

    @Test
    void updateInfo는_악기와_소개를_전체_교체한다() {
        MemberProfile profile = MemberProfile.create(member());
        profile.updateInfo(Set.of(Instrument.VIOLIN, Instrument.PIANO), "첫 소개");

        profile.updateInfo(Set.of(Instrument.CELLO), "수정된 소개");

        assertThat(profile.getInstruments()).containsExactly(Instrument.CELLO);
        assertThat(profile.getBio()).isEqualTo("수정된 소개");
    }

    @Test
    void changeImage는_key를_교체한다() {
        MemberProfile profile = MemberProfile.create(member());

        profile.changeImage("profile/2026/07/15/a.png");

        assertThat(profile.getProfileImageKey()).isEqualTo("profile/2026/07/15/a.png");
    }

    @Test
    void 악기는_21종이고_모두_한글_label을_가진다() {
        assertThat(Instrument.values()).hasSize(21);
        assertThat(Instrument.VIOLIN.getLabel()).isEqualTo("바이올린");
        assertThat(Instrument.VOICE.getLabel()).isEqualTo("성악");
        assertThat(Instrument.VOCAL.getLabel()).isEqualTo("보컬");
        for (Instrument instrument : Instrument.values()) {
            assertThat(instrument.getLabel()).isNotBlank();
        }
    }
}
```

`MemberProfileRepositoryTest.java`:

```java
package com.back.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.MemberProfile;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class MemberProfileRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberProfileRepository memberProfileRepository;

    private Member savedMember(String suffix) {
        return memberRepository.save(
                Member.createLocal("user" + suffix, "pw", suffix + "@attaca.com", "닉" + suffix));
    }

    @Test
    void memberId로_조회하고_악기_컬렉션이_왕복된다() {
        Member member = savedMember("a");
        MemberProfile profile = MemberProfile.create(member);
        profile.updateInfo(Set.of(Instrument.VIOLIN, Instrument.VOICE), "소개글");
        memberProfileRepository.saveAndFlush(profile);

        assertThat(memberProfileRepository.findByMemberId(member.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.getInstruments())
                            .containsExactlyInAnyOrder(Instrument.VIOLIN, Instrument.VOICE);
                    assertThat(found.getBio()).isEqualTo("소개글");
                });

        assertThat(memberProfileRepository.findByMemberId(999999L)).isEmpty();
    }

    @Test
    void 같은_회원의_프로필은_중복_저장할_수_없다() {
        Member member = savedMember("b");
        memberProfileRepository.saveAndFlush(MemberProfile.create(member));

        assertThatThrownBy(() ->
                memberProfileRepository.saveAndFlush(MemberProfile.create(member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
BE/gradlew -p BE test --tests "com.back.domain.member.entity.MemberProfileTest" --tests "com.back.domain.member.repository.MemberProfileRepositoryTest"
```

Expected: 컴파일 실패 — `Instrument`, `MemberProfile`이 없다.

- [ ] **Step 3: 최소 구현**

`Instrument.java`:

```java
package com.back.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로필에 등록할 수 있는 악기/전공. 코드가 API 값이고 {@code label}은 화면 표시용 한글이다.
 * 값 추가는 하위호환이지만 삭제/개명은 기존 데이터 마이그레이션이 필요하므로 주요 결정으로 기록 후 진행한다.
 * (밴드/대중 악기는 2026-07-15 리뷰에서 제외. 노래는 VOICE(성악)/VOCAL(보컬)로 분리)
 */
@Getter
@RequiredArgsConstructor
public enum Instrument {

    // 현악
    VIOLIN("바이올린"), VIOLA("비올라"), CELLO("첼로"), DOUBLE_BASS("더블베이스"), HARP("하프"),
    // 목관
    FLUTE("플루트"), OBOE("오보에"), CLARINET("클라리넷"), BASSOON("바순"),
    // 금관
    HORN("호른"), TRUMPET("트럼펫"), TROMBONE("트롬본"), TUBA("튜바"),
    // 건반
    PIANO("피아노"), ORGAN("오르간"),
    // 성악/보컬
    VOICE("성악"), VOCAL("보컬"),
    // 기타
    PERCUSSION("타악기"), COMPOSITION("작곡"), CONDUCTING("지휘"), ETC("그 외");

    private final String label;
}
```

`MemberProfile.java`:

```java
package com.back.domain.member.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 프로필(악기/자기소개/프로필 이미지). Member와 1:1 단방향 — Member 쪽에는 참조를 두지 않아
 * 인증 등 프로필 무관 조회에 컬렉션이 딸려오지 않게 한다.
 * 가입 시 만들지 않고 첫 수정/이미지 업로드 때 생성한다(lazy upsert).
 * 이미지는 storageKey만 보관하고 URL은 저장하지 않는다(DOMAIN-COMMON-STATUTE §7).
 */
@Entity
@Table(name = "member_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_profile_instrument",
            joinColumns = @JoinColumn(name = "member_profile_id"))
    @Column(name = "instrument", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Instrument> instruments = new HashSet<>();

    @Column(length = 500)
    private String bio;

    @Column(name = "profile_image_key")
    private String profileImageKey;

    private MemberProfile(Member member) {
        this.member = member;
    }

    public static MemberProfile create(Member member) {
        return new MemberProfile(member);
    }

    /** 악기·소개를 전체 교체한다(부분 수정 아님 — PUT 시맨틱). */
    public void updateInfo(Set<Instrument> instruments, String bio) {
        this.instruments.clear();
        this.instruments.addAll(instruments);
        this.bio = bio;
    }

    public void changeImage(String newKey) {
        this.profileImageKey = newKey;
    }
}
```

`MemberProfileRepository.java`:

```java
package com.back.domain.member.repository;

import com.back.domain.member.entity.MemberProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {

    Optional<MemberProfile> findByMemberId(Long memberId);
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
BE/gradlew -p BE test --tests "com.back.domain.member.entity.MemberProfileTest" --tests "com.back.domain.member.repository.MemberProfileRepositoryTest"
```

Expected: PASS (6개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/entity/ BE/src/main/java/com/back/domain/member/repository/ BE/src/test/java/com/back/domain/member/
git commit -m "feat: Instrument enum(21종) 및 MemberProfile 엔티티·리포지토리 추가"
```

---

## Task 4: 프로필 DTO + MemberProfileService

`FileService`를 Fake `FileStorage`로 검증하는 기존 `FileServiceTest` 패턴을 그대로 쓴다 — 디스크/AWS를 건드리지 않는다.

**Files:**
- Create: `BE/src/main/java/com/back/domain/member/dto/ProfileResponse.java`
- Create: `BE/src/main/java/com/back/domain/member/dto/UpdateProfileRequest.java`
- Create: `BE/src/main/java/com/back/domain/member/dto/ProfileImageResponse.java`
- Create: `BE/src/main/java/com/back/domain/member/service/MemberProfileService.java`
- Test: `BE/src/test/java/com/back/domain/member/service/MemberProfileServiceTest.java`

**Interfaces:**
- Consumes: `MemberProfile`/`Instrument`/`MemberProfileRepository`(Task 3), `MEMBER_NOT_FOUND`(Task 2), 기존 `FileService.upload(MultipartFile, String, Long)`→`StoredFile(id, storageKey, url)` / `delete(String)` / `getUrl(String)`, `ErrorCode.INVALID_FILE`
- Produces:
  - `ProfileResponse(List<Instrument> instruments, String bio, String profileImageUrl)` — `instruments`는 이름순 정렬(응답 안정성), 정적 팩토리 `from(MemberProfile, String url)` / `empty()`
  - `UpdateProfileRequest(@Size(max = 10) List<Instrument> instruments, @Size(max = 500) String bio)`
  - `ProfileImageResponse(String profileImageUrl)`
  - `MemberProfileService.getMyProfile(Long)` / `updateMyProfile(Long, UpdateProfileRequest)` / `updateProfileImage(Long, MultipartFile)`

- [ ] **Step 1: 실패하는 테스트 작성**

`MemberProfileServiceTest.java`:

```java
package com.back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.dto.ProfileImageResponse;
import com.back.domain.member.dto.ProfileResponse;
import com.back.domain.member.dto.UpdateProfileRequest;
import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberProfileRepository;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileMetadataRepository;
import com.back.global.storage.FileService;
import com.back.global.storage.FileStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

// FileStorage 를 Fake 로 대체해 디스크/AWS 없이 서비스 로직을 검증한다(FileServiceTest 패턴).
@DataJpaTest
class MemberProfileServiceTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberProfileRepository memberProfileRepository;
    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private FakeFileStorage fileStorage;
    private MemberProfileService service;

    @BeforeEach
    void setUp() {
        fileStorage = new FakeFileStorage();
        FileService fileService = new FileService(fileStorage, fileMetadataRepository);
        service = new MemberProfileService(memberRepository, memberProfileRepository, fileService);
    }

    private Member savedMember(String suffix) {
        return memberRepository.save(
                Member.createLocal("user" + suffix, "pw", suffix + "@attaca.com", "닉" + suffix));
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "얼굴.png", "image/png",
                "img-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 프로필이_없으면_빈_기본값을_돌려준다() {
        Member member = savedMember("a");

        ProfileResponse response = service.getMyProfile(member.getId());

        assertThat(response.instruments()).isEmpty();
        assertThat(response.bio()).isNull();
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void 첫_수정_호출이_프로필을_생성한다() {
        Member member = savedMember("b");

        ProfileResponse response = service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(List.of(Instrument.VIOLIN, Instrument.PIANO), "소개"));

        assertThat(response.instruments())
                .containsExactly(Instrument.PIANO, Instrument.VIOLIN); // 이름순 정렬
        assertThat(response.bio()).isEqualTo("소개");
        assertThat(memberProfileRepository.findByMemberId(member.getId())).isPresent();
    }

    @Test
    void 재수정은_전체_교체다() {
        Member member = savedMember("c");
        service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(List.of(Instrument.VIOLIN), "이전"));

        ProfileResponse response = service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(List.of(Instrument.CELLO), "이후"));

        assertThat(response.instruments()).containsExactly(Instrument.CELLO);
        assertThat(response.bio()).isEqualTo("이후");
    }

    @Test
    void null_목록은_빈_목록으로_간주한다() {
        Member member = savedMember("d");

        ProfileResponse response = service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(null, "소개만"));

        assertThat(response.instruments()).isEmpty();
    }

    @Test
    void 없는_회원의_수정은_MEMBER_NOT_FOUND() {
        assertThatThrownBy(() -> service.updateMyProfile(999999L,
                new UpdateProfileRequest(List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 이미지를_업로드하면_key가_저장되고_url을_돌려준다() {
        Member member = savedMember("e");

        ProfileImageResponse response = service.updateProfileImage(member.getId(), pngFile());

        String savedKey = memberProfileRepository.findByMemberId(member.getId())
                .orElseThrow().getProfileImageKey();
        assertThat(savedKey).startsWith("profile/").endsWith(".png");
        assertThat(response.profileImageUrl()).isEqualTo("http://fake/" + savedKey);
        assertThat(fileStorage.stored).containsKey(savedKey);
        assertThat(fileMetadataRepository.findByStorageKey(savedKey)).isPresent();
    }

    @Test
    void 이미지_교체시_옛_파일과_메타데이터가_삭제된다() {
        Member member = savedMember("f");
        service.updateProfileImage(member.getId(), pngFile());
        String oldKey = memberProfileRepository.findByMemberId(member.getId())
                .orElseThrow().getProfileImageKey();

        service.updateProfileImage(member.getId(), pngFile());

        String newKey = memberProfileRepository.findByMemberId(member.getId())
                .orElseThrow().getProfileImageKey();
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(fileStorage.stored).doesNotContainKey(oldKey).containsKey(newKey);
        assertThat(fileMetadataRepository.findByStorageKey(oldKey)).isEmpty();
    }

    @Test
    void 이미지가_아닌_파일은_거절한다() {
        Member member = savedMember("g");
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.updateProfileImage(member.getId(), pdf))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }

    /** 디스크/AWS 없이 검증하기 위한 인메모리 저장소. */
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
BE/gradlew -p BE test --tests "com.back.domain.member.service.MemberProfileServiceTest"
```

Expected: 컴파일 실패 — DTO·`MemberProfileService`가 없다.

- [ ] **Step 3: 최소 구현**

`ProfileResponse.java`:

```java
package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.MemberProfile;
import java.util.Comparator;
import java.util.List;

/** 내 프로필 응답. {@code instruments}는 이름순 정렬로 응답 순서를 고정한다. */
public record ProfileResponse(List<Instrument> instruments, String bio, String profileImageUrl) {

    public static ProfileResponse from(MemberProfile profile, String profileImageUrl) {
        List<Instrument> sorted = profile.getInstruments().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return new ProfileResponse(sorted, profile.getBio(), profileImageUrl);
    }

    /** 프로필 미생성 회원용 빈 기본값(404 대신 — FE가 편집 화면을 그리기 쉽다). */
    public static ProfileResponse empty() {
        return new ProfileResponse(List.of(), null, null);
    }
}
```

`UpdateProfileRequest.java`:

```java
package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 프로필 전체 교체 요청(PUT). {@code instruments}가 null이면 빈 목록으로 간주한다. */
public record UpdateProfileRequest(
        @Size(max = 10, message = "악기는 최대 10개까지 선택할 수 있습니다.") List<Instrument> instruments,
        @Size(max = 500, message = "자기소개는 500자를 넘을 수 없습니다.") String bio) {
}
```

`ProfileImageResponse.java`:

```java
package com.back.domain.member.dto;

/** 프로필 이미지 교체 응답. */
public record ProfileImageResponse(String profileImageUrl) {
}
```

`MemberProfileService.java`:

```java
package com.back.domain.member.service;

import com.back.domain.member.dto.ProfileImageResponse;
import com.back.domain.member.dto.ProfileResponse;
import com.back.domain.member.dto.UpdateProfileRequest;
import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.MemberProfile;
import com.back.domain.member.repository.MemberProfileRepository;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileService;
import com.back.global.storage.StoredFile;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필 서비스. 프로필은 가입 시 만들지 않고 첫 수정/업로드 때 생성한다(lazy upsert).
 * 파일은 FileService만 경유한다(FileStorage 직접 호출 금지).
 */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private static final String PROFILE_IMAGE_DIRECTORY = "profile";

    private final MemberRepository memberRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(Long memberId) {
        return memberProfileRepository.findByMemberId(memberId)
                .map(this::toResponse)
                .orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse updateMyProfile(Long memberId, UpdateProfileRequest request) {
        MemberProfile profile = getOrCreate(memberId);
        Set<Instrument> instruments = request.instruments() == null
                ? Set.of()
                : Set.copyOf(request.instruments());
        profile.updateInfo(instruments, request.bio());
        return toResponse(profile);
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(Long memberId, MultipartFile file) {
        validateImage(file);
        MemberProfile profile = getOrCreate(memberId);
        String oldKey = profile.getProfileImageKey();

        StoredFile stored = fileService.upload(file, PROFILE_IMAGE_DIRECTORY, memberId);
        profile.changeImage(stored.storageKey());

        // 새 이미지 저장이 확정된 뒤에만 옛 파일을 제거한다 — 실패해도 이미지 유실 없음.
        if (oldKey != null) {
            fileService.delete(oldKey);
        }
        return new ProfileImageResponse(stored.url());
    }

    /** 이미지 타입 정책은 MEMBER 도메인의 책임(DOMAIN-COMMON-STATUTE §7). 크기 상한은 전역 multipart 설정이 담당. */
    private void validateImage(MultipartFile file) {
        if (file == null || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private MemberProfile getOrCreate(Long memberId) {
        return memberProfileRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                    return memberProfileRepository.save(MemberProfile.create(member));
                });
    }

    private ProfileResponse toResponse(MemberProfile profile) {
        String url = profile.getProfileImageKey() == null
                ? null
                : fileService.getUrl(profile.getProfileImageKey());
        return ProfileResponse.from(profile, url);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
BE/gradlew -p BE test --tests "com.back.domain.member.service.MemberProfileServiceTest"
```

Expected: PASS (8개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/dto/ BE/src/main/java/com/back/domain/member/service/MemberProfileService.java BE/src/test/java/com/back/domain/member/service/MemberProfileServiceTest.java
git commit -m "feat: MemberProfileService 및 프로필 DTO 추가 - upsert·이미지 교체"
```

---

## Task 5: MemberProfileController + 선택지 API

인증이 필요한 첫 MEMBER API다. principal은 `JwtAuthenticationFilter`가 심는 `Long memberId`이며 `@AuthenticationPrincipal Long`으로 받는다.

**Files:**
- Create: `BE/src/main/java/com/back/domain/member/dto/ProfileOptionsResponse.java`
- Create: `BE/src/main/java/com/back/domain/member/controller/MemberProfileController.java`
- Test: `BE/src/test/java/com/back/domain/member/controller/MemberProfileControllerTest.java`

**Interfaces:**
- Consumes: `MemberProfileService`(Task 4), `Instrument`(Task 3), 기존 `ApiResponse`, `JwtProvider.createAccessToken(Long, Role)`(테스트용)
- Produces:
  - `GET /api/members/me/profile` → `ApiResponse<ProfileResponse>`
  - `PUT /api/members/me/profile` → `ApiResponse<ProfileResponse>` (`@Valid`)
  - `PUT /api/members/me/profile/image` → `ApiResponse<ProfileImageResponse>` (multipart, 파트명 `file`)
  - `GET /api/members/profile-options` → `ApiResponse<ProfileOptionsResponse>`

- [ ] **Step 1: 실패하는 테스트 작성**

`MemberProfileControllerTest.java`:

```java
package com.back.domain.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class MemberProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String bearer;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(
                Member.createLocal("profileuser", "pw", "profile@attaca.com", "프로필유저"));
        bearer = "Bearer " + jwtProvider.createAccessToken(member.getId(), Role.USER);
    }

    @Test
    void 토큰_없이_프로필_조회는_401() throws Exception {
        mockMvc.perform(get("/api/members/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-01"));
    }

    @Test
    void 프로필이_없으면_빈_기본값_200() throws Exception {
        mockMvc.perform(get("/api/members/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.instruments").isEmpty())
                .andExpect(jsonPath("$.data.bio").isEmpty())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
    }

    @Test
    void 프로필을_수정하면_조회에_반영된다() throws Exception {
        mockMvc.perform(put("/api/members/me/profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\": [\"VIOLIN\", \"VOICE\"], \"bio\": \"안녕하세요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("안녕하세요"));

        mockMvc.perform(get("/api/members/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instruments[0]").value("VIOLIN"))
                .andExpect(jsonPath("$.data.instruments[1]").value("VOICE"));
    }

    @Test
    void 없는_악기_코드는_400() throws Exception {
        mockMvc.perform(put("/api/members/me/profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\": [\"VIOLINN\"], \"bio\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 자기소개_500자_초과는_400() throws Exception {
        String longBio = "가".repeat(501);
        mockMvc.perform(put("/api/members/me/profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\": [], \"bio\": \"" + longBio + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-01"));
    }

    @Test
    void 이미지를_업로드하면_url을_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "얼굴.png", "image/png",
                "img".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/members/me/profile/image")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isNotEmpty());
    }

    @Test
    void 이미지가_아닌_파일은_400() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/members/me/profile/image")
                        .file(pdf)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.resultCode").value("400-02"));
    }

    @Test
    void 선택지_목록은_악기_21종을_담는다() throws Exception {
        mockMvc.perform(get("/api/members/profile-options").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instruments.length()").value(Instrument.values().length))
                .andExpect(jsonPath("$.data.instruments[0].code").value("VIOLIN"))
                .andExpect(jsonPath("$.data.instruments[0].label").value("바이올린"));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
BE/gradlew -p BE test --tests "com.back.domain.member.controller.MemberProfileControllerTest"
```

Expected: 컴파일 실패 — `ProfileOptionsResponse`, 컨트롤러가 없다.

- [ ] **Step 3: 최소 구현**

`ProfileOptionsResponse.java`:

```java
package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import java.util.Arrays;
import java.util.List;

/** FE가 선택지를 하드코딩하지 않도록 enum 목록을 제공한다. 순서는 enum 선언 순서(악기군별 묶음). */
public record ProfileOptionsResponse(List<OptionItem> instruments) {

    public record OptionItem(String code, String label) {
    }

    public static ProfileOptionsResponse create() {
        List<OptionItem> instruments = Arrays.stream(Instrument.values())
                .map(i -> new OptionItem(i.name(), i.getLabel()))
                .toList();
        return new ProfileOptionsResponse(instruments);
    }
}
```

`MemberProfileController.java`:

```java
package com.back.domain.member.controller;

import com.back.domain.member.dto.ProfileImageResponse;
import com.back.domain.member.dto.ProfileOptionsResponse;
import com.back.domain.member.dto.ProfileResponse;
import com.back.domain.member.dto.UpdateProfileRequest;
import com.back.domain.member.service.MemberProfileService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필 API. 모두 인증 필요(기본 인가 규칙) — principal은 JWT의 회원 id(Long).
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    @GetMapping("/me/profile")
    public ApiResponse<ProfileResponse> getMyProfile(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(memberProfileService.getMyProfile(memberId));
    }

    @PutMapping("/me/profile")
    public ApiResponse<ProfileResponse> updateMyProfile(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(memberProfileService.updateMyProfile(memberId, request));
    }

    @PutMapping("/me/profile/image")
    public ApiResponse<ProfileImageResponse> updateProfileImage(@AuthenticationPrincipal Long memberId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(memberProfileService.updateProfileImage(memberId, file));
    }

    @GetMapping("/profile-options")
    public ApiResponse<ProfileOptionsResponse> profileOptions() {
        return ApiResponse.success(ProfileOptionsResponse.create());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인 후 전체 빌드**

```bash
BE/gradlew -p BE test --tests "com.back.domain.member.controller.MemberProfileControllerTest"
BE/gradlew -p BE clean build
```

Expected: 둘 다 PASS / BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/controller/MemberProfileController.java BE/src/main/java/com/back/domain/member/dto/ProfileOptionsResponse.java BE/src/test/java/com/back/domain/member/controller/MemberProfileControllerTest.java
git commit -m "feat: 회원 프로필 API 4종 추가 - 조회/수정/이미지/선택지"
```

---

## Task 6: 문서 반영

**Files:**
- Modify: `docs/DOMAIN-MEMBER-STATUTE.md`
- Modify: `docs/ARCHITECTURE-STATUTE.md`
- Modify: `docs/CONTEXT.md`
- Modify: `docs/TODO-READY.md` / `docs/TODO-DONE.md`
- Modify: `docs/AI-ACTION-LOGS.md` / `docs/AI-MAJOR-EVENT.md` / `docs/AI-MAJOR-EVENT-RECAP.md`

- [ ] **Step 1: `DOMAIN-MEMBER-STATUTE.md` 갱신**

§2의 `### MemberProfile` 블록을 다음으로 교체(초안의 `genres` 줄 삭제):

```markdown
### MemberProfile (구현 완료, 2026-07-15)
* id
* member (Member 1:1 단방향, `member_id` unique — Member 쪽엔 참조 없음)
* instruments (`Instrument` enum 21종, `@ElementCollection` `member_profile_instrument`. 장르 필드는 2026-07-15 리뷰에서 제외 — 클래식 중심)
* bio (자기소개, 최대 500자)
* profileImageKey (`FileService` 경유 업로드. URL은 저장하지 않고 `FileService.getUrl`로 생성)
* 생성 시점: 가입 시 만들지 않고 첫 수정/이미지 업로드 때 생성(lazy upsert)
```

§2 하단의 `> 악기/장르를 문자열 목록으로 둘지 …(MemberProfile은 미구현)` 인용문을 삭제한다.

§3.1의 API 목록 아래에 추가:

```markdown
### 3.2 프로필 API (구현 완료, 2026-07-15 — 모두 인증 필요, principal = JWT 회원 id)

* `GET /api/members/me/profile` : 내 프로필. 미생성 시 빈 기본값(404 아님) → `ProfileResponse{instruments[], bio, profileImageUrl}`
* `PUT /api/members/me/profile` : 전체 교체 upsert. body `{instruments: [코드], bio}` (악기 최대 10개, bio 최대 500자)
* `PUT /api/members/me/profile/image` : multipart(`file`) 이미지 교체. `image/*`만 허용(위반 시 400-02), 새 파일 저장 확정 후 옛 파일 삭제
* `GET /api/members/profile-options` : 악기 선택지 `{code, label}` 목록
* 에러코드 추가: `MEMBER_NOT_FOUND`(404-03)
```

- [ ] **Step 2: `ARCHITECTURE-STATUTE.md` §1 갱신**

`* 데이터: Spring Data JPA + MySQL` 줄을 다음으로 교체:

```markdown
* 데이터: Spring Data JPA + MySQL (로컬 개발은 레포 루트 `docker-compose.yml`의 MySQL 8.4, `docker compose up -d`. 테스트는 H2 `test` 프로파일)
```

- [ ] **Step 3: `CONTEXT.md` 갱신**

`## 현재 상태`의 단계 줄을 교체:

```markdown
* 단계: BE 전역 기반 + 보안 + MEMBER 인증/프로필 + 파일 저장 + 런타임 DB(MySQL) 완료. 다음은 FE 초기화(+CORS) 또는 구글 소셜 확장 또는 다음 도메인 문서화.
```

`## 주의`에서 `* 테스트 DB는 H2… bootRun(로컬 서버 기동) 자체가 불가 …` 항목을 다음으로 교체:

```markdown
* 런타임 DB: MySQL(레포 루트 `docker-compose.yml`, `docker compose up -d` 후 `bootRun`). 데이터소스는 env 기본값(DB_URL/DB_USERNAME/DB_PASSWORD), `ddl-auto: update`(운영 전환 시 재결정).
* 테스트는 H2 `test` 프로파일: `@SpringBootTest`에는 반드시 `@ActiveProfiles("test")`를 붙일 것(없으면 MySQL 접속 시도로 실패). `application-test.yaml`이 datasource/storage 루트를 덮어쓴다.
* 검증: Bean Validation 도입(`@Valid`). 검증 실패·본문 파싱 실패(enum 오타)·multipart 파트 누락은 400-01로 매핑(과거 500 결함 수정).
```

`## 주의`의 MEMBER API 항목 끝에 추가:

```markdown
* MEMBER 프로필 API(인증 필요): `GET/PUT /api/members/me/profile`, `PUT /api/members/me/profile/image`(image/*만), `GET /api/members/profile-options`. `Instrument` enum 21종(VOICE=성악/VOCAL=보컬 분리, 장르 없음). `MEMBER_NOT_FOUND`(404-03).
```

- [ ] **Step 4: TODO 갱신**

`TODO-READY.md`에서 "BE 런타임 DB 구성"·"MEMBER: 프로필…" 두 항목을 제거한다(남는 것: FE 초기화).

`TODO-DONE.md` 맨 위(`---` 아래)에 추가:

```markdown
* [x] (2026-07-15) BE 런타임 DB(MySQL) + MEMBER 프로필/이미지 (TDD, subagent-driven)
  * 런타임 DB: 레포 루트 docker-compose(MySQL 8.4) + datasource env 기본값 + `ddl-auto: update`. 테스트는 `application-test.yaml`(H2) 프로파일 분리(`@SpringBootTest`에 `@ActiveProfiles("test")`)
  * `MemberProfile`(1:1 단방향, lazy upsert) + `Instrument` enum 21종(장르는 리뷰에서 제외, VOICE/VOCAL 분리)
  * API 4종: `GET/PUT /api/members/me/profile`, `PUT /api/members/me/profile/image`(image/* 검증, 교체 시 옛 파일 삭제), `GET /api/members/profile-options`
  * Bean Validation 도입 + 전역 예외 400 매핑 3건(@Valid 실패/본문 파싱 실패/파트 누락 — 기존 500 결함 수정), `MEMBER_NOT_FOUND`(404-03)
  * 파일 저장 계층(FileService)의 첫 실사용처
```

- [ ] **Step 5: 기록 문서 갱신**

`AI-ACTION-LOGS.md` 맨 위에 추가:

```markdown
* 2026-07-15 — 런타임 DB(MySQL docker-compose) + MEMBER 프로필/이미지 TDD 구현(브랜치 feature/be-runtime-db-member-profile). 테스트 H2 프로파일 분리, Bean Validation 도입, 클라이언트 실수 3종 400 매핑(기존 500 결함 수정), MemberProfile+Instrument(21종)+API 4종. 전체 clean build 통과.
```

`AI-MAJOR-EVENT.md` 맨 아래에 추가:

```markdown
## 2026-07-15 — 런타임 DB 구성 및 MEMBER 프로필 설계 확정

### 주요 의사결정
* **런타임 DB**: Docker Compose 기반 MySQL 8.4 채택(재현 가능·버전 고정). 데이터소스는 env 기본값, `ddl-auto`는 개발 단계 `update`(운영 전환 시 validate+마이그레이션 도구 재결정). 테스트는 `application-test.yaml`(H2) 프로파일로 분리 — main yaml에 MySQL이 생기면 `@SpringBootTest`가 MySQL 접속을 시도하므로 `@ActiveProfiles("test")` 필수.
* **악기 표현**: 자유 문자열 대신 **enum 고정 목록(21종)** 채택 — 표기 통일·추후 구인/구직 필터 대비. 노래는 VOICE(성악)/VOCAL(보컬)로 분리(클래식 성악과 대중 보컬은 다른 영역). 밴드/대중 악기(기타/베이스/드럼)는 당분간 미취급으로 제외.
* **장르 필드 제외**: 서비스가 당분간 클래식 중심이라 장르 구분의 실익이 없어 프로필에서 제외. 필요 시 enum 추가로 재도입(하위호환).
* **프로필 생성 시점**: 가입 시 자동 생성하지 않고 첫 수정/업로드 때 생성(lazy upsert). GET은 미생성 시 404가 아닌 빈 기본값 응답(FE 편집 화면 친화).
* **선행 결함 수정**: @Valid 실패·본문 파싱 실패·multipart 파트 누락이 catch-all에 걸려 500으로 응답되던 것을 400-01로 정정.
```

`AI-MAJOR-EVENT-RECAP.md` 맨 아래에 추가:

```markdown
* **2026-07-15 런타임 DB + MEMBER 프로필**: docker-compose MySQL로 `bootRun` 가능해짐(테스트는 H2 프로파일 분리).
  * 프로필: 악기 enum 21종(VOICE=성악/VOCAL=보컬 분리, 장르 제외), lazy upsert, 이미지는 FileService 경유(교체 시 옛 파일 삭제)
  * Bean Validation 도입, 클라이언트 실수 3종이 500으로 응답되던 결함을 400으로 정정
```

- [ ] **Step 6: 최종 전체 빌드 및 커밋**

```bash
BE/gradlew -p BE clean build
```

Expected: BUILD SUCCESSFUL

```bash
git add docs/
git commit -m "docs: 런타임 DB·MEMBER 프로필 구현 반영 및 주요 결정 기록"
```

---

## 완료 후 남는 것 (플랜 범위 밖)

- **노션 동기화** — 컨트롤러(메인 세션)가 병합 후 수행: TODO 보드 2건 DONE, 스케줄 이벤트 추가.
- **MySQL 실기동 수동 검증** — Task 1 Step 6을 건너뛴 경우, 병합 후 사용자 환경에서 확인.
- FE 초기화 + CORS(TODO-READY/BACKLOG), 다른 회원 프로필 조회·이미지 삭제 API(소비처 생길 때).
