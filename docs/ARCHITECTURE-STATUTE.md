# ARCHITECTURE-STATUTE

아키텍처 구현 규칙. 구조가 바뀌면 이 문서를 갱신한다.

---

## 1. 기술 스택

### Backend

* 언어/런타임: Java 21
* 프레임워크: Spring Boot 3.4.x (안정 버전 사용, 4.x는 사용하지 않는다)
* 빌드: Gradle (Kotlin DSL, `build.gradle.kts`)
* 데이터: Spring Data JPA + MySQL (로컬 개발은 레포 루트 `docker-compose.yml`의 MySQL 8.4, `docker compose up -d`. 테스트는 H2 `test` 프로파일)
* 보안: Spring Security
  * 자체 회원가입: JWT 기반 인증
  * 소셜 로그인: OAuth2 (카카오/구글 등)
* 실시간 채팅: WebSocket(STOMP) + Redis Pub/Sub
* 파일 저장: `FileStorage` 인터페이스로 추상화 — 로컬 디스크(기본값, 개발용) / AWS S3 서울 리전(opt-in, `storage.type=s3`) 두 구현체

### Frontend

* Next.js 16 (App Router) / React 19 / TypeScript / Tailwind CSS v4 / Vitest. 패키지 매니저 npm.
* 위치: `FE/`. BE와 독립 실행(`cd FE && npm run dev`, 기본 :3000).
* **BFF 패턴**: 브라우저는 Next(same-origin)하고만 통신하고, Next 서버가 Spring을 서버 간 호출한다.
  토큰은 httpOnly 쿠키(`access_token`/`refresh_token`)로 다루며 UI JavaScript는 토큰을 만지지 않는다.
  * 계층: `lib/server/*`(순수 로직·쿠키·reissue) → `app/api/bff/**`(라우트 핸들러 글루) → `app/**`(UI).
  * BE 호출 주소는 서버 env `BE_BASE_URL`(클라이언트 노출 금지). 통신은 네이티브 fetch(라이브러리 미도입).
  * 소셜 로그인: 카카오는 서버 라우트 `/api/bff/oauth/kakao/start`(state 발급→카카오 302)와 `/api/bff/oauth/kakao/callback`(state 대조→BE 코드교환→쿠키)로 처리. CSRF `state`는 서버 생성·httpOnly 쿠키(`oauth_state`)·단일사용. `KAKAO_CLIENT_ID`/`KAKAO_REDIRECT_URI`는 서버 env.
  * 프로필: `/profile`(조회/수정 모드) + BFF `PUT /api/bff/me/profile`(악기·자기소개), `PUT /api/bff/me/profile/image`(멀티파트 즉시 업로드), `GET /api/bff/profile-options`. 멀티파트 위해 `beFetch`는 body가 FormData면 content-type을 붙이지 않는다.
* 동일 BE API를 모바일 앱이 재사용할 수 있도록 API 소비 방식을 플랫폼 독립적으로 유지한다.

---

## 2. BE 패키지 구조

도메인별 패키지 + 계층형 구조를 사용한다.

```
com.back
├── domain
│   ├── member
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── verifiedperformer
│   ├── feed
│   ├── performance
│   ├── recruitment
│   └── chat
└── global
    ├── config          // 설정 (Security, WebSocket, JPA, S3 등)
    ├── security        // 인증/인가, JWT, OAuth2
    ├── exception       // 전역 예외 처리, 공통 예외, 에러 코드
    ├── common          // BaseEntity, 공통 응답 래퍼 등
    └── storage         // FileStorage 인터페이스 및 구현
```

* 각 도메인은 위 계층(controller/service/repository/entity/dto)을 자체적으로 갖는다.
* 도메인 간 협력은 서비스 계층을 통해서만 한다.

---

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

---

## 4. 실시간 채팅 규칙

* 클라이언트-서버 실시간 통신은 WebSocket(STOMP)으로 한다.
* 다중 서버 확장을 대비해 메시지 브로드캐스트는 Redis Pub/Sub를 경유한다.
* 채팅 메시지는 DB에 영속화한다. (히스토리 조회 지원)

---

## 5. 배포 (추후 결정)

* Nginx 리버스 프록시(`/api` → BE, 그 외 → FE), HTTPS(SSL) 종료, WebSocket 프록시는 **배포 단계에서 결정**한다.
* 로컬 개발 단계에서는 도입하지 않는다. (Next dev 서버 + Spring 직접 실행)

---

## 6. 테스트 규칙

* 도메인별 단위 테스트와 통합 테스트를 작성한다.
* 가능하면 TDD로 진행한다.
* 모든 테스트/린트 통과를 작업 완료의 기준으로 한다.
