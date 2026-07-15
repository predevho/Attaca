# FE 카카오 소셜 로그인 연동(BFF + CSRF state) 설계

> 작성일: 2026-07-15
> 상태: 설계 승인 완료 → 구현 계획(writing-plans) 전환
> 관련 문서: `docs/DOMAIN-MEMBER-STATUTE.md §3`, `docs/superpowers/specs/2026-07-15-fe-init-auth-bff-design.md`
> 선행 완료: BE 카카오 소셜(2026-07-13), FE 인증 플로우 BFF(2026-07-15)

---

## 1. 목표와 범위

### 목표

이미 만든 FE BFF 인증 위에 **카카오 소셜 로그인**을 얹는다. 프론트 주도 + 백엔드 코드교환
방식으로, 브라우저가 카카오에서 받은 인가코드를 BFF가 BE(`POST /api/auth/oauth/kakao`)로
교환해 httpOnly 쿠키 세션을 만든다. OAuth CSRF를 막기 위해 `state` 파라미터를 서버에서
생성·검증한다.

### 범위 (In)

- 서버 start 라우트: `GET /api/bff/oauth/kakao/start` — state 생성 + 카카오 authorize로 302
- 서버 callback 라우트: `GET /api/bff/oauth/kakao/callback` — state 검증 + BE 교환 + 쿠키 + 리다이렉트
- CSRF state: httpOnly 쿠키 저장·대조·단일사용
- `lib/server/oauthState.ts`, `lib/server/kakao.ts` (순수 로직, 단위 테스트)
- `/login`에 "카카오 로그인" 버튼 + `?error=` 안내 메시지
- 서버 env: `KAKAO_CLIENT_ID`, `KAKAO_REDIRECT_URI`
- Vitest 단위 테스트 + 배선 수동 검증(실제 카카오 왕복은 키 확보 후로 보류)

### 범위 밖 (Out)

- **실제 카카오 로그인 성공 왕복 검증** — 카카오 앱 키 미확보. 배선만 로컬 검증, 실제 성공은 키 확보 후 수동. (BE 카카오와 동일 전략)
- 카카오 계정 수동 연결/해제 관리 UI
- 다른 provider(구글 등)
- state를 Redis 등 서버 저장 — 현재 httpOnly 쿠키로 충분
- 카카오 로그아웃(카카오 세션 종료) 연동 — 앱 로그아웃은 기존 쿠키 삭제로 충분

---

## 2. 흐름 (완전 서버측 BFF)

```
/login "카카오 로그인" 버튼 (링크 이동, fetch 아님)
   │
   ▼
GET /api/bff/oauth/kakao/start
   │  state 난수 생성 → oauth_state httpOnly 쿠키 저장(TTL 10분)
   │  카카오 authorize URL 구성(client_id, redirect_uri, response_type=code, state)
   ▼  302 Location: https://kauth.kakao.com/oauth/authorize?...
카카오 로그인/동의
   │  카카오가 redirect_uri로 302 (code, state 동봉) — 사용자 취소 시 error 동봉
   ▼
GET /api/bff/oauth/kakao/callback?code=&state=
   │  1) oauth_state 쿠키 읽어 URL state와 대조 → 불일치/부재면 /login?error=state
   │  2) error 파라미터 있으면 /login?error=kakao_cancelled
   │  3) BE POST /api/auth/oauth/kakao {code, redirectUri} 교환
   │  4) 성공 → access/refresh httpOnly 쿠키 설정 → state 쿠키 삭제 → 302 /dashboard
   │     실패 → state 쿠키 삭제 → 302 /login?error=oauth
   ▼
/dashboard  또는  /login?error=...
```

- **콜백은 클라이언트 페이지가 아니라 서버 GET 라우트 핸들러다.** 카카오 `redirect_uri`가
  이 라우트를 직접 가리키므로 code·state·쿠키·BE교환이 전부 서버에서 처리되고 토큰이 UI에
  노출되지 않는다. 별도 콜백 페이지 컴포넌트는 만들지 않는다.
- 기존 자체 로그인 BFF(`/api/bff/login`)와 동일하게 `setAuthCookies`로 수렴한다.

---

## 3. CSRF state (확정)

- **생성**: start 라우트에서 `crypto.randomUUID()`로 state 문자열 생성.
- **저장**: `oauth_state` 쿠키 — `httpOnly: true`, `sameSite: 'lax'`, `secure: production`, `path: '/'`, `maxAge: 600`(10분).
- **대조**: 콜백에서 URL `state`와 쿠키 값이 **정확히 일치**해야 통과. 쿠키 없음/불일치 → 거절(`/login?error=state`).
- **단일 사용**: 콜백은 성공·실패·거절 어느 경로로 끝나든 `oauth_state` 쿠키를 삭제한다.
- state 로직은 `lib/server/oauthState.ts`에 쿠키 저장소 주입형으로 두어 단위 테스트한다.

---

## 4. 설정 (서버 env)

`.env.local.example`에 추가:

```
# 카카오 소셜 로그인 (서버 전용, NEXT_PUBLIC_ 금지)
KAKAO_CLIENT_ID=            # 카카오 REST API 키(공개값이나 서버 env로 관리)
KAKAO_REDIRECT_URI=http://localhost:3000/api/bff/oauth/kakao/callback
```

- `redirect_uri`는 authorize 요청과 BE 교환(`OAuthLoginRequest.redirectUri`) **양쪽에 같은 값**으로 전달한다(카카오가 일치를 검증).
- `KAKAO_CLIENT_ID`가 비어 있으면 start 라우트는 설정 누락으로 `/login?error=oauth_config`로 리다이렉트(로컬에서 키 없이도 앱이 죽지 않게).
- 카카오 `client_secret`은 FE에 두지 않는다 — 코드교환은 BE가 자기 `KAKAO_CLIENT_SECRET`으로 수행.

---

## 5. 파일 구조

```
FE/
├── lib/server/oauthState.ts      # state 생성/쿠키 저장/대조/삭제 (순수, 주입형)
├── lib/server/kakao.ts           # authorize URL 빌더 + BE 교환 호출
├── app/api/bff/oauth/kakao/start/route.ts      # GET → 302 kakao
├── app/api/bff/oauth/kakao/callback/route.ts   # GET → 검증·교환·쿠키·리다이렉트
└── app/(auth)/login/page.tsx     # (수정) 카카오 버튼 + error 메시지
```

### 인터페이스(초안)

- `oauthState.ts`: 상수 `STATE_COOKIE='oauth_state'`; `issueState(store) → string`(생성+쿠키설정); `verifyState(store, urlState) → boolean`; `clearState(store)`.
- `kakao.ts`: `buildAuthorizeUrl(state) → string`(env 사용); `exchangeCode(code, redirectUri) → BeResult`(BE `/api/auth/oauth/kakao` 호출, 기존 `beFetch` 재사용).

---

## 6. 에러 처리

| 상황 | 감지 | 결과 |
|---|---|---|
| 카카오 설정 누락 | start: `KAKAO_CLIENT_ID` 없음 | 302 `/login?error=oauth_config` |
| 사용자 취소/카카오 거부 | callback: `?error=` 존재 | 302 `/login?error=kakao_cancelled` |
| CSRF state 불일치/부재 | callback: 쿠키≠URL state | 302 `/login?error=state` |
| BE 교환 실패(미검증 이메일 401-08, provider 오류 502-01 등) | callback: BE `ok=false` | 302 `/login?error=oauth` |
| 정상 | callback: BE `ok=true` | 쿠키 설정 → 302 `/dashboard` |

- `/login` 페이지가 `searchParams.error`를 읽어 한글 안내를 폼 상단에 표시.
  (`state`→"보안 검증에 실패했습니다. 다시 시도해 주세요.", `kakao_cancelled`→"카카오 로그인이 취소되었습니다.", `oauth`→"카카오 로그인에 실패했습니다.", `oauth_config`→"카카오 로그인이 현재 설정되지 않았습니다.")
- 콜백은 항상 리다이렉트로 끝난다(사용자에게 raw JSON을 보이지 않음).

---

## 7. 테스트 전략

| 대상 | 방식 |
|---|---|
| `oauthState.ts` | fake store로 issue(쿠키 옵션·값)·verify(일치/불일치/부재)·clear 검증 |
| `kakao.ts` buildAuthorizeUrl | env 목킹 후 URL 파라미터(client_id/redirect_uri/response_type=code/state) 및 인코딩 검증 |
| callback route | `next/headers` 쿠키 + `fetch`(BE) 목킹: state통과+BE성공→인증쿠키 설정+`/dashboard` 리다이렉트, state불일치→`/login?error=state`(BE 미호출), BE실패→`/login?error=oauth` |
| start route | env 목킹: state 쿠키 설정 + Location이 카카오 authorize URL인지 |
| `/login` error 메시지 | 렌더 스모크(각 error 코드→문구) |

- `npm --prefix FE run build`(타입체크) + `npm --prefix FE test` 통과가 완료 기준.
- **실제 카카오 왕복은 자동 테스트에 넣지 않는다.** 키 확보 후 수동 검증하고 결과를 `AI-ACTION-LOGS.md`에 남긴다.

### 로컬 배선 수동 검증(키 없이 가능한 범위)

1. `/api/bff/oauth/kakao/start` 접근 → 카카오 authorize URL로 302 + `oauth_state` 쿠키 심김 확인(단, `KAKAO_CLIENT_ID` 미설정이면 `oauth_config`로 리다이렉트되는 것 확인).
2. 콜백에 가짜 code + 맞는 state로 접근 → BE까지 도달해 `OAUTH_PROVIDER_ERROR`(키 없음)로 `/login?error=oauth` (=배선 정상).
3. 콜백에 state 불일치 → 즉시 `/login?error=state`(BE 미도달).

---

## 8. 문서 반영(구현 완료 시)

- `ARCHITECTURE-STATUTE` FE 절 — 카카오 OAuth start/callback BFF 라우트, CSRF state 쿠키 한 줄
- `CONTEXT.md` — 카카오 FE 흐름·env·미검증 상태
- `TODO-BACKLOG` — "카카오 소셜 FE 연동" 항목 닫기(실키 수동검증만 잔여), 구글 provider FE는 유지
- `AI-ACTION-LOGS`, `AI-MAJOR-EVENT`(CSRF state·서버 콜백 결정), 노션

---

## 9. 후속 작업

- 카카오 앱 키 확보 후 실제 로그인 왕복 수동 검증
- 구글 등 타 provider FE 연동(동일 start/callback 패턴 일반화 검토)
- 로그인 상태에서 소셜 계정 연결/해제 관리
