# FE(Next.js) 초기화 + 인증 플로우(BFF) 설계

> 작성일: 2026-07-15
> 상태: 설계 승인 완료 → 구현 계획(writing-plans) 전환
> 관련 문서: `docs/ARCHITECTURE-CONSTITUTION.md §2`, `docs/ARCHITECTURE-STATUTE.md §1`, `docs/DOMAIN-MEMBER-STATUTE.md §3`
> 선행 완료: BE 인증(로그인/재발급, 2026-07-11~13), 런타임 DB(2026-07-15)

---

## 1. 목표와 범위

### 목표

`FE/`에 Next.js 프로젝트를 세우고, BE 인증 API(회원가입/로그인/재발급)와 연동해 **인증
플로우**를 완성한다. 토큰은 BFF(Backend-For-Frontend) 패턴으로 httpOnly 쿠키에 담아 UI
JavaScript가 토큰을 만지지 않게 한다. 지금까지 만든 BE 인증이 실제 화면으로 동작함을 검증한다.

### 범위 (In)

- `FE/` Next.js 스캐폴딩: App Router / TypeScript / Tailwind CSS / ESLint / Vitest, npm
- BFF 3계층: 서버 전용 BE 클라이언트, `app/api/bff/**` 라우트 핸들러, 클라이언트 UI
- httpOnly 쿠키 기반 세션(access/refresh)
- 화면: 회원가입 `/signup`, 로그인 `/login`, 보호된 페이지 `/dashboard`(로그인 확인용 1개)
- `middleware.ts` 라우트 보호 + 401 시 refresh 자동 재발급 1회 재시도
- 로그아웃(쿠키 삭제)
- Vitest 단위 테스트(쿠키 헬퍼·reissue 재시도·ApiResponse 언랩)
- 문서 반영: `ARCHITECTURE-STATUTE`에 FE 절 추가, CONTEXT/TODO/기록

### 범위 밖 (Out)

- **프로필 화면**(조회/수정/이미지) — 다음 작업. BE API는 준비돼 있음.
- **카카오 소셜 로그인 FE 연동** — 인가코드 리다이렉트 흐름은 별도 작업.
- **회원가입 직후 자동 로그인** — 가입 후 로그인 페이지로 유도만.
- **refresh 토큰 로테이션**(BE 무상태 유지, BACKLOG).
- **디자인 시스템/공통 레이아웃 고도화** — 최소 Tailwind 스타일만.
- **BE CORS 설정** — §4 참조(BFF라 브라우저 경로에 불필요).

---

## 2. 아키텍처 — BFF 3계층

브라우저는 오직 Next.js(same-origin)하고만 통신한다. Next 서버가 Spring을 대신 호출한다.

```
브라우저 ──(same-origin :3000)──▶ Next.js route handler ──(server-to-server :8080)──▶ Spring BE
   ▲ httpOnly 쿠키(access/refresh)                       ▲ Authorization: Bearer <access>
```

| 계층 | 위치 | 책임 | 토큰 접근 |
|---|---|---|---|
| ① 서버 BE 클라이언트 | `lib/server/beClient.ts` | Spring 호출, `ApiResponse` 언랩, 에러 정규화 | Bearer 헤더 부착(서버) |
| ② BFF 라우트 핸들러 | `app/api/bff/**/route.ts` | 쿠키 읽기/설정/삭제 + ①에 위임 | httpOnly 쿠키 R/W |
| ③ 클라이언트 UI | `app/(auth)/**`, `app/dashboard/**` | 폼·화면, `fetch('/api/bff/*')`만 호출 | **없음(토큰 안 만짐)** |

- 경계 원칙: ③은 `lib/server/*`를 import하지 않는다(서버 전용 코드). ①은 `next/headers`
  쿠키 API를 모른다(순수 fetch). 쿠키는 오직 ②에서만 다룬다.
- 서버 전용 파일은 상단에 `import 'server-only';`를 두어 클라이언트 번들 유입을 컴파일 타임에 차단.

### 통신 방식 — 네이티브 fetch (결정, 2026-07-15)

두 구간 모두 **런타임 내장 `fetch`**를 사용한다. axios·React Query(TanStack Query) 등 데이터
페칭 라이브러리는 도입하지 않는다.

- ① Next→Spring: `beClient.ts`에서 `fetch(BE_BASE_URL + path)`. Node 18+/Next 15 전역 fetch라
  의존성 0. `Authorization: Bearer` 부착 + `ApiResponse` 언랩은 이 계층에서.
- ③ 브라우저→BFF: 클라이언트 컴포넌트가 `fetch('/api/bff/*')` 상대경로 호출. 반복 축소를 위해
  `lib/api.ts`에 얇은 래퍼(`postBff(path, body)`)만 둔다.
- 근거: 이번 범위는 JSON POST 몇 개 + 쿠키 전달이 전부라 라이브러리의 인터셉터·캐싱 이점이
  이득보다 번들 비용이 크다. reissue 재시도도 `session.ts`가 fetch를 감싸는 얇은 함수로 충분.
- 재검토 시점: 프로필·목록 등 화면이 늘어 통신 지연 대응(캐싱·중복요청 제거)이나 대규모
  상태 동기화가 필요해지면 React Query 도입을 그때 논의한다. (사용자 확인, 2026-07-15)

---

## 3. 토큰·쿠키 전략

- 로그인 성공 시 ②가 BE에서 받은 `accessToken`/`refreshToken`을 **httpOnly 쿠키 2개**로 설정.
  - 옵션: `httpOnly: true`, `sameSite: 'lax'`, `secure: NODE_ENV === 'production'`, `path: '/'`.
  - `access_token` 만료 30분, `refresh_token` 만료 14일(BE 토큰 만료와 맞춤: 각각 `maxAge` 1800초 / 1209600초).
- UI는 쿠키를 읽지 않는다. 로그인 여부는 서버(미들웨어/서버 컴포넌트)가 판단한다.
- 쿠키 이름·옵션은 `lib/server/cookies.ts` 한 곳에서 상수로 관리(중복 금지).

---

## 4. CORS 결정 (문서 충돌 정리)

BFF에서 브라우저→Next는 same-origin, Next→Spring은 **서버 간 호출**이라 CORS(브라우저 보안
기제)가 적용되지 않는다. 따라서 **이번 작업에서 BE에 CORS 설정을 추가하지 않는다.**

- `TODO-BACKLOG`의 "CORS 설정(FE↔BE)" 항목을 닫고 사유를 남긴다: "BFF 채택으로 브라우저
  경로에 CORS 불요. 모바일 앱 등 브라우저가 Spring을 직접 호출하는 소비처가 생기면 재검토."
- 이는 애초 CORS를 넣으려던 계획과 달라진 지점이므로 `AI-MAJOR-EVENT`에 결정으로 기록한다.

---

## 5. 인증 플로우

### 5-1. 회원가입 `/signup`

- 폼: `loginId`, `password`, `email`, `nickname`.
- 제출 → `POST /api/bff/signup` → ② → BE `POST /api/auth/signup`.
- 성공: `/login`으로 이동(자동 로그인 안 함, 범위 밖). 실패: BE `error.message`를 폼에 표시
  (중복 loginId/email/nickname → 409-0x 메시지).

### 5-2. 로그인 `/login`

- 폼: `loginId`, `password`.
- 제출 → `POST /api/bff/login` → ② → BE `POST /api/auth/login` → `{accessToken, refreshToken}`
  수신 → ②가 httpOnly 쿠키 2개 설정 → 성공 반환 → `/dashboard`로 이동.
- 실패: 401-07 `LOGIN_FAILED` 메시지 표시.

### 5-3. 보호된 페이지 `/dashboard`

- 로그인 확인용 최소 화면(예: "환영합니다" + 로그아웃 버튼).
- `middleware.ts`가 `access_token` 쿠키 없으면 `/login`으로 리다이렉트.
  - 미들웨어는 쿠키 **존재 여부만** 검사(서명 검증 안 함 — 검증은 BE가 실제 API 호출 때 한다).
    미들웨어에 JWT 시크릿을 두지 않기 위함. 만료된 access여도 통과시키고, 실제 데이터 호출 시
    reissue가 처리한다.

### 5-4. 자동 재발급 (401 → reissue → 1회 재시도)

② 또는 ①이 BE 호출에서 401을 받으면:
1. `refresh_token` 쿠키로 BE `POST /api/auth/reissue`(body `{refreshToken}`) 호출.
2. 성공 → 새 `accessToken`을 쿠키에 갱신 → **원 요청 1회 재시도**.
3. reissue도 실패(refresh 만료/무효) → access·refresh 쿠키 삭제 → 401 응답(UI는 `/login`으로).
- 재시도는 정확히 1회. 무한 루프 방지 플래그를 둔다.

### 5-5. 로그아웃

- `POST /api/bff/logout` → 두 쿠키 삭제 → `/login`으로.

---

## 6. BFF 엔드포인트 계약

| BFF 경로 | 메서드 | 위임 대상(BE) | 쿠키 동작 |
|---|---|---|---|
| `/api/bff/signup` | POST | `POST /api/auth/signup` | 없음 |
| `/api/bff/login` | POST | `POST /api/auth/login` | access·refresh 설정 |
| `/api/bff/logout` | POST | 없음(로컬) | access·refresh 삭제 |

- 응답은 BE `ApiResponse{success, data, error}`를 그대로 통과. UI는 `success`/`error.message`만 본다.
- reissue(`/api/auth/reissue`)는 BFF 공개 엔드포인트가 아니라 ①/② 내부 재시도 로직에서만 호출.

---

## 7. 에러 처리

- ① `beClient`: BE 응답을 파싱해 `{ ok: boolean, data?, error?: {resultCode, code, message}, status }`
  형태로 정규화. 네트워크 오류·비-JSON 응답은 `{ ok: false, error: 일반 메시지, status: 0/5xx }`.
- ② BFF: ①의 결과를 HTTP 상태로 변환해 UI에 전달(BE status 보존).
- ③ UI: `error.message`를 폼 상단 또는 필드 근처에 표시. 예상 밖 오류는 일반 문구.

---

## 8. 디렉터리 구조(초안)

```
FE/
├── app/
│   ├── (auth)/login/page.tsx
│   ├── (auth)/signup/page.tsx
│   ├── dashboard/page.tsx
│   ├── api/bff/login/route.ts
│   ├── api/bff/signup/route.ts
│   ├── api/bff/logout/route.ts
│   ├── layout.tsx
│   └── page.tsx            // 루트 → /login 또는 /dashboard 리다이렉트
├── lib/
│   ├── server/beClient.ts  // 'server-only' — Spring 호출
│   ├── server/cookies.ts   // 쿠키 이름·옵션·set/clear
│   ├── server/session.ts   // reissue 재시도 래퍼
│   └── api.ts              // 클라이언트용 fetch('/api/bff/*') 헬퍼
├── middleware.ts
├── __tests__/              // Vitest
├── .env.local.example      // BE_BASE_URL=http://localhost:8080
├── vitest.config.ts
└── (create-next-app 기본 파일들)
```

- `BE_BASE_URL`은 서버 환경변수(클라이언트 노출 금지 — `NEXT_PUBLIC_` 접두사 안 씀).

---

## 9. 테스트 전략

| 대상 | 방식 |
|---|---|
| `cookies.ts` | 쿠키 옵션·set/clear 헬퍼 단위 테스트(옵션 값 검증) |
| `session.ts` reissue 재시도 | BE fetch를 목으로 두고 401→reissue→재시도 1회, reissue 실패 시 쿠키 삭제 검증 |
| `beClient.ts` | ApiResponse 언랩(success/error), 비-JSON·네트워크 오류 정규화 |
| UI 스모크 | 로그인·회원가입 폼 렌더링 1~2개(React Testing Library + Vitest) |
| 실연동 | 수동: BE(`docker compose up -d` + bootRun) 띄우고 가입→로그인→/dashboard→로그아웃 |

- `npm run build`(타입체크 포함)와 `npm test`가 통과해야 완료.

---

## 10. 문서 반영(구현 완료 시)

- `ARCHITECTURE-STATUTE` — FE 절 보강: App Router/TS/Tailwind, BFF 3계층, httpOnly 쿠키 토큰 전략, `BE_BASE_URL` 서버 env
- `ARCHITECTURE-CONSTITUTION` §2 — "BFF를 통한 same-origin 접근" 한 줄(FE/BE 분리 원칙과 정합)
- `CONTEXT.md` — FE 실행법(`cd FE && npm run dev`), BFF/쿠키 주의
- `TODO-BACKLOG` — CORS 항목 닫기(사유), 프로필 화면·카카오 FE 연동 추가
- `TODO-READY`→`DONE`, `AI-ACTION-LOGS`, `AI-MAJOR-EVENT`(BFF·쿠키·CORS 미추가 결정)
- 노션 동기화

---

## 11. 후속 작업

- MEMBER 프로필 화면(조회/수정/이미지) — 다음 순위
- 카카오 소셜 로그인 FE(인가코드 리다이렉트 → BFF 교환)
- 공통 레이아웃·네비게이션·디자인 다듬기
- E2E 테스트(Playwright 등) 도입 검토
