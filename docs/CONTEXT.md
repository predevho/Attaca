# CONTEXT

현재 작업 수행에 필요한 최소 정보만 유지한다. 로그 저장소로 쓰지 않는다.

---

## 현재 상태

* 단계: BE(인증/프로필/파일/DB) + FE 인증 플로우(Next.js BFF) 완료. 다음은 MEMBER 프로필 화면 또는 카카오 소셜 FE 또는 다음 도메인.
* 확정된 기술 스택
  * BE: Spring Boot 3.4.x / Java 21 / MySQL / Spring Security(JWT + OAuth2) / WebSocket(STOMP)+Redis / FileStorage 추상화(로컬 기본/S3 opt-in)
  * FE: Next.js 16(App Router)/React 19/TS/Tailwind/Vitest, 위치 `FE/`. BFF+httpOnly 쿠키. `cd FE && npm run dev`(:3000)
* 도메인 6개: MEMBER, VERIFIED-PERFORMER, FEED, PERFORMANCE, RECRUITMENT(구인/구직), CHAT
* 어드민: 별도 도메인 아님. MEMBER의 ROLE_ADMIN.

## 주의

* BE 스택: Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21(toolchain). Gradle 9는 Boot 3.4 미지원이므로 래퍼 올리지 말 것.
* 런타임 DB: MySQL(레포 루트 `docker-compose.yml`, `docker compose up -d` 후 `bootRun`). 데이터소스는 env 기본값(DB_URL/DB_USERNAME/DB_PASSWORD), `ddl-auto: update`(운영 전환 시 재결정).
* 테스트는 H2 `test` 프로파일: `@SpringBootTest`에는 반드시 `@ActiveProfiles("test")`를 붙일 것(없으면 MySQL 접속 시도로 실패). `application-test.yaml`이 datasource/storage 루트를 덮어쓴다.
* 검증: Bean Validation 도입(`@Valid`). 검증 실패·본문 파싱 실패(enum 오타)·multipart 파트 누락은 400-01로 매핑(과거 500 결함 수정).
* 도메인 문서 없이 해당 도메인 구현 금지. 현재 문서화된 도메인: COMMON, MEMBER.
* 코드 스타일: 단순 필드 접근자는 Lombok `@Getter`로 통일(수동 getter 금지).
* 응답 에러 본문은 `ErrorBody(resultCode:String, code:String, message)`. `resultCode`는 `HTTP상태-일련번호` 문자열(400-01/405-01/500-01).
* 보안: 무상태 JWT(access+refresh, `/api/auth/reissue`). `jwt.secret`은 env(`JWT_SECRET`) 주입·커밋 금지. 인증 ErrorCode 401-01~08, 403-01.
* MEMBER 식별자: `loginId`=자체 로그인 열쇠(unique, nullable) / `email`=인증·소셜연결 키(unique, 전원 필수) / `nickname`=활동명(unique) / 내부 신원=`id`. `password`/`loginId`는 소셜 전용 회원에서 null(자체 로그인 경로에 null 가드).
* MEMBER API(모두 `/api/auth/**` permit): `POST /signup{loginId,password,email,nickname}`, `POST /login{loginId,password}`, `POST /oauth/kakao{code,redirectUri}`. 로그인/소셜 모두 access+refresh 발급. 비번 BCrypt.
* MEMBER 프로필 API(인증 필요): `GET/PUT /api/members/me/profile`, `PUT /api/members/me/profile/image`(image/*만), `GET /api/members/profile-options`. `Instrument` enum 21종(VOICE=성악/VOCAL=보컬 분리, 장르 없음). `MEMBER_NOT_FOUND`(404-03).
* 소셜: 프론트 인가코드→백엔드 교환(`OAuthClient`/`KakaoOAuthClient`). 검증된 이메일만 자동연결, 미검증 거절(401-08). 카카오 키는 env(`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`) 주입·커밋 금지.
* MEMBER 에러코드(전역 `ErrorCode`): EMAIL/NICKNAME/LOGIN_ID_ALREADY_EXISTS 409-01/02/03, LOGIN_FAILED 401-07, OAUTH_EMAIL_UNVERIFIED 401-08, OAUTH_PROVIDER_ERROR 502-01.
* 파일 저장: `FileStorage`(바이트) + `FileService`(key생성·메타데이터). 도메인은 `FileService`만 사용. `storage.type`=local(기본)/s3. 로컬은 `/files/**`로 서빙(SecurityConfig permit).
* S3 키(`S3_BUCKET`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)는 env 주입·커밋 금지. **실제 S3 연동은 아직 미검증**(자격증명 미발급, 자동 테스트는 S3Client 목).
* 일반 에러코드에 `RESOURCE_NOT_FOUND`(404-02) 추가: 매칭되는 핸들러가 없는 모든 URL(앱 전역, `NoResourceFoundException`)이 이전에는 catch-all(`Exception.class`)에 걸려 500으로 잘못 응답되던 버그를 수정. `FILE_NOT_FOUND`(404-01)와는 별개(파일 저장소 전용이 아님).
* `LocalFileServingConfig`는 `WebMvcConfigurer`를 구현해 `@WebMvcTest`가 자동으로 끌어온다. 신규 `@WebMvcTest` 슬라이스 작성 시 `StorageProperties` 빈이 없으면 컨텍스트 로딩이 실패하므로 목/설정 빈을 함께 준비할 것.
* FE 인증: BFF 3계층(`lib/server/*`→`app/api/bff/**`→UI). 토큰은 httpOnly 쿠키, UI는 토큰 안 만짐. 통신은 네이티브 fetch(라이브러리 미도입). BE 주소는 서버 env `BE_BASE_URL`. `/dashboard`는 미들웨어가 쿠키 존재로 보호, reissue는 `lib/server/session.ts`가 401 시 1회 재시도. 실연동은 BE 기동 후 수동 검증.

## 보류된 결정

* `ErrorCode`를 인터페이스로 승격해 도메인별 에러 코드(예: `MemberErrorCode`)를 분리할지 여부 → 지금 확정하지 않음. 개발 진행하며 명확해질 때 결정. (현재는 전역 enum 단일 구조)

## 다음 작업

* TODO-READY.md 참고.
