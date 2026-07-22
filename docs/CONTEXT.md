# CONTEXT

현재 작업 수행에 필요한 최소 정보만 유지한다. 로그 저장소로 쓰지 않는다.

---

## 현재 상태

* 단계: BE(인증/프로필/파일/DB + VERIFIED-PERFORMER + FEED + PERFORMANCE + RECRUITMENT) + FE(인증/프로필/카카오) 완료. 다음은 FE 화면(인증연주자/피드/공연/구인) 또는 남은 도메인 CHAT(문서 선작성 + Redis 필요).
* 확정된 기술 스택
  * BE: Spring Boot 3.4.x / Java 21 / MySQL / Spring Security(JWT + OAuth2) / WebSocket(STOMP)+Redis / FileStorage 추상화(로컬 기본/S3 opt-in)
  * FE: Next.js 16(App Router)/React 19/TS/Tailwind/Vitest, 위치 `FE/`. BFF+httpOnly 쿠키. `cd FE && npm run dev`(:3000)
* 도메인 6개: MEMBER, VERIFIED-PERFORMER, FEED, PERFORMANCE, RECRUITMENT(구인/구직), CHAT
* 어드민: 별도 도메인 아님. MEMBER의 ROLE_ADMIN.

## 주의

* BE 스택: Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21(toolchain). Gradle 9는 Boot 3.4 미지원이므로 래퍼 올리지 말 것.
* 런타임 DB: MySQL(레포 루트 `docker-compose.yml`, `docker compose up -d` 후 `bootRun`). 데이터소스는 env 기본값(DB_URL/DB_USERNAME/DB_PASSWORD), `ddl-auto: update`(운영 전환 시 재결정).
  * ⚠️ 이 개발 PC엔 시스템 환경변수 `DB_PASSWORD=1234`가 설정돼 있어 compose 기본값(`attaca-local`)을 덮어써 `bootRun`이 `Access denied`로 실패한다. 해결: `gradlew bootRun --args=--spring.datasource.password=attaca-local`로 override(명령행이 env보다 우선)하거나 `DB_PASSWORD`를 unset. compose 볼륨이 낡으면 `docker compose down -v` 후 재기동.
* 테스트는 H2 `test` 프로파일: `@SpringBootTest`에는 반드시 `@ActiveProfiles("test")`를 붙일 것(없으면 MySQL 접속 시도로 실패). `application-test.yaml`이 datasource/storage 루트를 덮어쓴다.
* 검증: Bean Validation 도입(`@Valid`). 검증 실패·본문 파싱 실패(enum 오타)·multipart 파트 누락은 400-01로 매핑(과거 500 결함 수정).
* 도메인 문서 없이 해당 도메인 구현 금지. 현재 구현된 도메인: COMMON, MEMBER, VERIFIED-PERFORMER(2026-07-16 BE), FEED(2026-07-17 BE), PERFORMANCE(2026-07-22 BE), RECRUITMENT(2026-07-23 BE). 미착수(문서도 없음): CHAT.
* PERFORMANCE(연주회): 엔티티 `Performance`(organizerId=원시 Long, title/description/performedAt/venue/program(자유텍스트)/ticketInfo/ticketUrl/posterImageKey, soft delete). API 모두 `/api/performances`(인증 필요): 등록(POST)·목록(GET `?scope=upcoming|past|all`, Spring Pageable)·상세(GET/{id})·수정(PUT/{id})·삭제(DELETE/{id})·포스터(PUT/{id}/poster, image/*). **등록=인증 연주자 또는 ROLE_ADMIN**(`VerifiedPerformerService.isVerified` 협력, 아니면 403-02), 수정=주최자, 삭제=주최자 또는 ADMIN(어드민 전용 경로 없음). 주최자 표시(닉네임+인증뱃지)는 `MemberQueryService.findDisplaysByIds` 배치 재사용(N+1 없음). 포스터는 FileService(교체 시 옛파일 삭제). 목록 size 기본 20/최대 50. 에러코드 404-07(PERFORMANCE_NOT_FOUND)/403-02(NOT_VERIFIED_PERFORMER).
* RECRUITMENT(구인): 엔티티 2종 `RecruitmentPosting`(authorId=원시 Long, instruments=다중 악기 `@ElementCollection`+`@BatchSize(100)`, recruitCount/location/fee/deadline(nullable=상시), status OPEN/CLOSED, soft delete) + `RecruitmentApplication`(postingId/applicantId 원시 Long, message, 상태머신 PENDING→ACCEPTED/REJECTED/WITHDRAWN). API 모두 `/api/recruitments`(인증 필요). **구인만**(구직 범위 밖). 공고: 등록(POST, **인증 회원 누구나** — PERFORMANCE와 달리 게이팅 없음)·목록(GET `?scope=open|closed|all&instrument=`, Pageable)·상세·수정(PUT, 작성자)·마감(POST `/{id}/close`, 작성자)·삭제(DELETE, 작성자·ADMIN). 지원: `POST /{id}/applications`(본인공고/마감/중복 시 409)·`GET /{id}/applications`(작성자만)·`GET /applications/me`·`POST /applications/{aid}/accept|reject`(작성자)·`/withdraw`(지원자). 마감 판정 파생=`status==CLOSED || (deadline!=null && now>=deadline)`, deadline==now는 CLOSED. 활성 지원(PENDING/ACCEPTED) 유일(best-effort check-then-save), 거절/철회 뒤 재지원 허용. 표시정보는 `MemberQueryService.findDisplaysByIds` 배치(N+1 없음). 에러코드 404-08(RECRUITMENT_NOT_FOUND)/404-09(RECRUITMENT_APPLICATION_NOT_FOUND)/409-07(RECRUITMENT_CLOSED)/409-08(ALREADY_APPLIED)/409-09(CANNOT_APPLY_OWN_RECRUITMENT)/409-10(RECRUITMENT_INVALID_APPLICATION_STATE). 목록 size 기본 20/최대 50.
* FEED(피드): 엔티티 4종 Post/Comment/PostLike/CommentLike(soft delete, 좋아요 유니크·멱등). 작성자는 원시 `authorId`(Long), 표시정보(닉네임+인증뱃지)는 `MemberQueryService.findDisplaysByIds` 배치 협력으로 파생(N+1 없음, fetch join 불가 — 도메인 경계상 연관 없음). API 모두 `/api/feed/**`(인증 필요): 게시글 CRUD+커서 타임라인(최신순), 댓글 작성/목록(커서 오래된순)/삭제, 좋아요(게시글·댓글) 멱등. 수정=작성자, 삭제=작성자 또는 ROLE_ADMIN(동일 엔드포인트, 어드민 전용 경로 없음). 커서 size 기본 20/최대 50. 에러코드 404-05(POST_NOT_FOUND)/404-06(COMMENT_NOT_FOUND). 좋아요 동시성은 유니크 제약+`saveAndFlush` catch로 멱등 200 보장.
* 코드 스타일: 단순 필드 접근자는 Lombok `@Getter`로 통일(수동 getter 금지).
* 응답 에러 본문은 `ErrorBody(resultCode:String, code:String, message)`. `resultCode`는 `HTTP상태-일련번호` 문자열(400-01/405-01/500-01).
* 보안: 무상태 JWT(access+refresh, `/api/auth/reissue`). `jwt.secret`은 env(`JWT_SECRET`) 주입·커밋 금지. 인증 ErrorCode 401-01~08, 403-01.
* MEMBER 식별자: `loginId`=자체 로그인 열쇠(unique, nullable) / `email`=인증·소셜연결 키(unique, 전원 필수) / `nickname`=활동명(unique) / 내부 신원=`id`. `password`/`loginId`는 소셜 전용 회원에서 null(자체 로그인 경로에 null 가드).
* MEMBER API(모두 `/api/auth/**` permit): `POST /signup{loginId,password,email,nickname}`, `POST /login{loginId,password}`, `POST /oauth/kakao{code,redirectUri}`. 로그인/소셜 모두 access+refresh 발급. 비번 BCrypt.
* MEMBER 프로필 API(인증 필요): `GET/PUT /api/members/me/profile`, `PUT /api/members/me/profile/image`(image/*만), `GET /api/members/profile-options`. `Instrument` enum 21종(VOICE=성악/VOCAL=보컬 분리, 장르 없음). `MEMBER_NOT_FOUND`(404-03).
* 소셜: 프론트 인가코드→백엔드 교환(`OAuthClient`/`KakaoOAuthClient`). 검증된 이메일만 자동연결, 미검증 거절(401-08). 카카오 키는 env(`KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET`) 주입·커밋 금지.
* VERIFIED-PERFORMER(인증 연주자): 상태머신 엔티티 `VerificationApplication`(PENDING/APPROVED/REJECTED/REVOKED, `memberId`=원시 Long, 재신청=새 레코드). 회원 API `POST/GET /api/verified-performers/applications(/me)`. 어드민 API `/api/admin/verified-performers/**`(ROLE_ADMIN): 목록(`?status`+Pageable)·`{id}/approve|reject|revoke`·`grant`(직접지정). 활성 신청(PENDING/APPROVED) 유일 → 재신청 409. 뱃지는 `VerifiedPerformerService.isVerified`(APPROVED만 true)로 파생, MEMBER `ProfileResponse.verified`가 서비스 협력으로 채움(엔티티 직접참조 없음). 에러코드 409-04(ALREADY_PENDING)/409-05(ALREADY_APPROVED)/409-06(INVALID_APPLICATION_STATE)/404-04(APPLICATION_NOT_FOUND).
* MEMBER 에러코드(전역 `ErrorCode`): EMAIL/NICKNAME/LOGIN_ID_ALREADY_EXISTS 409-01/02/03, LOGIN_FAILED 401-07, OAUTH_EMAIL_UNVERIFIED 401-08, OAUTH_PROVIDER_ERROR 502-01.
* 파일 저장: `FileStorage`(바이트) + `FileService`(key생성·메타데이터). 도메인은 `FileService`만 사용. `storage.type`=local(기본)/s3. 로컬은 `/files/**`로 서빙(SecurityConfig permit).
* S3 키(`S3_BUCKET`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)는 env 주입·커밋 금지. **실제 S3 연동은 아직 미검증**(자격증명 미발급, 자동 테스트는 S3Client 목).
* 일반 에러코드에 `RESOURCE_NOT_FOUND`(404-02) 추가: 매칭되는 핸들러가 없는 모든 URL(앱 전역, `NoResourceFoundException`)이 이전에는 catch-all(`Exception.class`)에 걸려 500으로 잘못 응답되던 버그를 수정. `FILE_NOT_FOUND`(404-01)와는 별개(파일 저장소 전용이 아님).
* `LocalFileServingConfig`는 `WebMvcConfigurer`를 구현해 `@WebMvcTest`가 자동으로 끌어온다. 신규 `@WebMvcTest` 슬라이스 작성 시 `StorageProperties` 빈이 없으면 컨텍스트 로딩이 실패하므로 목/설정 빈을 함께 준비할 것.
* FE 인증: BFF 3계층(`lib/server/*`→`app/api/bff/**`→UI). 토큰은 httpOnly 쿠키, UI는 토큰 안 만짐. 통신은 네이티브 fetch(라이브러리 미도입). BE 주소는 서버 env `BE_BASE_URL`. `/dashboard`는 미들웨어가 쿠키 존재로 보호, reissue는 `lib/server/session.ts`가 401 시 1회 재시도. 실연동은 BE 기동 후 수동 검증.
* FE 카카오 로그인: `/login` 버튼→`/api/bff/oauth/kakao/start`(CSRF state httpOnly 쿠키+카카오 302)→카카오→`/api/bff/oauth/kakao/callback`(state 대조→BE `/api/auth/oauth/kakao` 교환→인증쿠키→/dashboard). 에러는 `/login?error=`. `KAKAO_CLIENT_ID`/`KAKAO_REDIRECT_URI` 서버 env. **실제 카카오 왕복은 앱 키 미확보로 미검증**(배선만 목/로컬 확인).
* FE 프로필: `/profile`(인증 필요, 미들웨어 보호). 조회 기본 + 수정 모드(악기 칩 최대10·자기소개 500자, `PUT /api/bff/me/profile`). 이미지는 파일 선택 즉시 업로드(`PUT /api/bff/me/profile/image`, 멀티파트). 악기 코드↔label은 `/api/bff/profile-options`로 변환. `beFetch`는 FormData면 content-type 미설정(멀티파트).

## 보류된 결정

* `ErrorCode`를 인터페이스로 승격해 도메인별 에러 코드(예: `MemberErrorCode`)를 분리할지 여부 → 지금 확정하지 않음. 개발 진행하며 명확해질 때 결정. (현재는 전역 enum 단일 구조)

## 다음 작업

* TODO-READY.md 참고.
