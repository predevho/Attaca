# FE 카카오 소셜 로그인 연동(BFF + CSRF state) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 FE BFF 인증 위에 카카오 소셜 로그인을 얹는다 — 서버 start/callback 라우트로 인가코드를 BE와 교환하고, CSRF state를 서버에서 생성·검증한다.

**Architecture:** `/login`의 카카오 버튼이 서버 `start` 라우트로 이동 → state를 httpOnly 쿠키에 저장하고 카카오로 302 → 카카오가 서버 `callback` 라우트로 돌려보냄 → state 대조 후 BE 교환 → 인증 쿠키 설정 → `/dashboard`. 토큰·state·code가 전부 서버에서 처리되어 UI에 노출되지 않는다.

**Tech Stack:** Next.js 16(App Router) / TypeScript / Vitest 4 / 네이티브 fetch. 기존 `beFetch`·`cookies.ts`·BFF 패턴 재사용.

**설계 문서:** `docs/superpowers/specs/2026-07-15-fe-kakao-oauth-design.md`

## Global Constraints

- 위치 `FE/`. 통신은 네이티브 fetch만(라이브러리 금지). 토큰·state는 UI(클라이언트)에서 접근하지 않는다.
- `lib/server/*`는 최상단 `import 'server-only';`. 쿠키 R/W는 서버 라우트/서버 코드에서만.
- 카카오 `client_id`/`redirect_uri`는 **서버 env**(`KAKAO_CLIENT_ID`/`KAKAO_REDIRECT_URI`). `NEXT_PUBLIC_` 금지. `client_secret`은 FE에 두지 않는다(BE가 교환).
- state 쿠키: `oauth_state`, httpOnly, sameSite lax, secure=production, path '/', maxAge 600(10분), **단일 사용**(콜백에서 삭제).
- 콜백은 항상 리다이렉트로 끝난다(사용자에게 raw JSON 노출 금지). 에러는 `/login?error=<code>`.
- BE 계약: `POST /api/auth/oauth/kakao` body `{code, redirectUri}` → `{accessToken, refreshToken}`(성공) / `ApiResponse` 에러.
- 각 태스크 끝에서 `npm --prefix FE run build`와 `npm --prefix FE test` 통과.
- Gradle/서버 명령이 필요하면 `-p BE`/`--prefix FE` 명시. 커밋 메시지 한글.
- **실제 카카오 왕복은 자동 테스트에 넣지 않는다**(키 미확보). 목/로컬 배선만 검증.

---

## 기존 코드 재사용(선행 태스크 산출)

- `FE/lib/server/beClient.ts`: `beFetch(path, init?) → Promise<BeResult>`, `BeResult = {ok, status, data, message}`.
- `FE/lib/server/cookies.ts`: `type CookieStore`(set/delete/get), `setAuthCookies(store, access, refresh)`.
- Vitest 하네스: `server-only` 스텁 처리됨(`vitest.config.ts` alias + `vitest.setup.ts`). 이 태스크에서 하네스를 건드리지 않는다.

---

## File Structure

| 파일 | 작업 | 책임 |
|---|---|---|
| `FE/lib/server/oauthState.ts` | 생성 | state 생성·쿠키저장·대조·삭제(주입형, server-only) |
| `FE/lib/server/kakao.ts` | 생성 | authorize URL 빌더 + BE 교환 호출(server-only) |
| `FE/app/api/bff/oauth/kakao/start/route.ts` | 생성 | GET → state 발급 + 카카오 302 |
| `FE/app/api/bff/oauth/kakao/callback/route.ts` | 생성 | GET → state 대조 + BE 교환 + 쿠키 + 리다이렉트 |
| `FE/app/(auth)/login/page.tsx` | 수정(서버화) | `searchParams.error` 읽어 메시지 매핑 → LoginForm 렌더 |
| `FE/app/(auth)/login/LoginForm.tsx` | 생성 | 기존 폼 로직(클라이언트) + 카카오 버튼 + initialError |
| `FE/.env.local.example` | 수정 | KAKAO_CLIENT_ID/KAKAO_REDIRECT_URI 추가 |

---

## Task 1: oauthState + kakao (순수 로직 + 단위 테스트)

**Files:**
- Create: `FE/lib/server/oauthState.ts`, `FE/lib/server/kakao.ts`
- Test: `FE/__tests__/oauthState.test.ts`, `FE/__tests__/kakao.test.ts`

**Interfaces:**
- Consumes: `CookieStore`(cookies.ts), `beFetch`/`BeResult`(beClient.ts)
- Produces:
  - `oauthState.ts`: `STATE_COOKIE='oauth_state'`; `issueState(store) → string`; `verifyState(store, urlState: string|null) → boolean`; `clearState(store) → void`
  - `kakao.ts`: `buildAuthorizeUrl(state: string) → string`; `exchangeCode(code: string, redirectUri: string) → Promise<BeResult>`

- [ ] **Step 1: 실패 테스트 작성**

`FE/__tests__/oauthState.test.ts` (순수 서버 로직 — `crypto.randomUUID`가 확실히 되는 node 환경에서 실행):

```ts
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
import { STATE_COOKIE, issueState, verifyState, clearState } from '@/lib/server/oauthState';
import type { CookieStore } from '@/lib/server/cookies';

function fakeStore(): CookieStore & { jar: Record<string, string> } {
  const jar: Record<string, string> = {};
  return {
    jar,
    set: vi.fn((n: string, v: string) => { jar[n] = v; }),
    delete: vi.fn((n: string) => { delete jar[n]; }),
    get: (n: string) => (n in jar ? { value: jar[n] } : undefined),
  };
}

describe('oauthState', () => {
  it('issueState는 state를 만들고 httpOnly 쿠키에 저장한다', () => {
    const store = fakeStore();
    const state = issueState(store);

    expect(state).toBeTruthy();
    expect(store.jar[STATE_COOKIE]).toBe(state);
    const opts = (store.set as any).mock.calls[0][2];
    expect(opts).toMatchObject({ httpOnly: true, sameSite: 'lax', path: '/', maxAge: 600 });
  });

  it('verifyState는 쿠키와 URL state가 일치할 때만 true', () => {
    const store = fakeStore();
    const state = issueState(store);

    expect(verifyState(store, state)).toBe(true);
    expect(verifyState(store, 'other')).toBe(false);
    expect(verifyState(store, null)).toBe(false);
  });

  it('쿠키가 없으면 verifyState는 false', () => {
    const store = fakeStore();
    expect(verifyState(store, 'anything')).toBe(false);
  });

  it('clearState는 state 쿠키를 삭제한다', () => {
    const store = fakeStore();
    issueState(store);
    clearState(store);
    expect(store.jar[STATE_COOKIE]).toBeUndefined();
  });
});
```

`FE/__tests__/kakao.test.ts` (순수 서버 로직 — node 환경):

```ts
// @vitest-environment node
import { describe, it, expect, vi, afterEach } from 'vitest';
import { buildAuthorizeUrl, exchangeCode } from '@/lib/server/kakao';

const OLD_ENV = { ...process.env };
afterEach(() => { process.env = { ...OLD_ENV }; vi.unstubAllGlobals(); });

describe('buildAuthorizeUrl', () => {
  it('env로 카카오 authorize URL을 구성한다', () => {
    process.env.KAKAO_CLIENT_ID = 'rest-key';
    process.env.KAKAO_REDIRECT_URI = 'http://localhost:3000/api/bff/oauth/kakao/callback';

    const url = new URL(buildAuthorizeUrl('state-123'));

    expect(url.origin + url.pathname).toBe('https://kauth.kakao.com/oauth/authorize');
    expect(url.searchParams.get('client_id')).toBe('rest-key');
    expect(url.searchParams.get('redirect_uri')).toBe('http://localhost:3000/api/bff/oauth/kakao/callback');
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('state')).toBe('state-123');
  });
});

describe('exchangeCode', () => {
  it('BE /api/auth/oauth/kakao로 code와 redirectUri를 POST한다', async () => {
    const fetchMock = vi.fn(async () => new Response(
      JSON.stringify({ success: true, data: { accessToken: 'A', refreshToken: 'R' }, error: null }),
      { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await exchangeCode('auth-code', 'http://localhost:3000/api/bff/oauth/kakao/callback');

    expect(res.ok).toBe(true);
    expect(res.data).toEqual({ accessToken: 'A', refreshToken: 'R' });
    const [calledUrl, init] = fetchMock.mock.calls[0];
    expect(String(calledUrl)).toContain('/api/auth/oauth/kakao');
    expect(JSON.parse((init as RequestInit).body as string))
      .toEqual({ code: 'auth-code', redirectUri: 'http://localhost:3000/api/bff/oauth/kakao/callback' });
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `@/lib/server/oauthState`, `@/lib/server/kakao` 없음.

- [ ] **Step 3: 구현**

`FE/lib/server/oauthState.ts`:

```ts
import 'server-only';
import type { CookieStore } from '@/lib/server/cookies';

export const STATE_COOKIE = 'oauth_state';
const STATE_MAX_AGE = 600; // 10분

/** CSRF state를 생성해 httpOnly 쿠키에 저장하고 그 값을 반환한다. */
export function issueState(store: CookieStore): string {
  const state = crypto.randomUUID();
  store.set(STATE_COOKIE, state, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: STATE_MAX_AGE,
  });
  return state;
}

/** 쿠키의 state와 콜백 URL의 state가 정확히 일치하면 true. */
export function verifyState(store: CookieStore, urlState: string | null): boolean {
  const cookie = store.get(STATE_COOKIE)?.value;
  return !!cookie && !!urlState && cookie === urlState;
}

/** state 쿠키를 삭제한다(단일 사용). */
export function clearState(store: CookieStore): void {
  store.delete(STATE_COOKIE);
}
```

`FE/lib/server/kakao.ts`:

```ts
import 'server-only';
import { beFetch, type BeResult } from '@/lib/server/beClient';

const KAKAO_AUTHORIZE_URL = 'https://kauth.kakao.com/oauth/authorize';

/** 카카오 authorize URL을 구성한다. client_id/redirect_uri는 서버 env. */
export function buildAuthorizeUrl(state: string): string {
  const params = new URLSearchParams({
    client_id: process.env.KAKAO_CLIENT_ID ?? '',
    redirect_uri: process.env.KAKAO_REDIRECT_URI ?? '',
    response_type: 'code',
    state,
  });
  return `${KAKAO_AUTHORIZE_URL}?${params.toString()}`;
}

/** BE에 인가코드를 넘겨 토큰으로 교환한다. redirectUri는 authorize와 동일해야 한다. */
export function exchangeCode(code: string, redirectUri: string): Promise<BeResult> {
  return beFetch('/api/auth/oauth/kakao', {
    method: 'POST',
    body: JSON.stringify({ code, redirectUri }),
  });
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
npm --prefix FE test
```

Expected: PASS (신규 8개 포함).

- [ ] **Step 5: 커밋**

```bash
git add FE/lib/server/oauthState.ts FE/lib/server/kakao.ts FE/__tests__/oauthState.test.ts FE/__tests__/kakao.test.ts
git commit -m "feat: 카카오 OAuth state 관리 및 authorize/교환 헬퍼 추가"
```

---

## Task 2: start + callback BFF 라우트 + env

**Files:**
- Create: `FE/app/api/bff/oauth/kakao/start/route.ts`, `FE/app/api/bff/oauth/kakao/callback/route.ts`
- Modify: `FE/.env.local.example`
- Test: `FE/__tests__/kakao-callback.test.ts`

**Interfaces:**
- Consumes: `issueState`/`verifyState`/`clearState`(Task 1), `buildAuthorizeUrl`/`exchangeCode`(Task 1), `setAuthCookies`(cookies.ts), `next/headers` `cookies()`, `next/server` `NextResponse`
- Produces: `GET /api/bff/oauth/kakao/start`, `GET /api/bff/oauth/kakao/callback`

- [ ] **Step 1: 실패 테스트 작성 (callback)**

`FE/__tests__/kakao-callback.test.ts` (라우트 핸들러 — DOM 불필요, node 환경):

```ts
// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const jar: Record<string, string> = {};
const cookieStore = {
  set: vi.fn((n: string, v: string) => { jar[n] = v; }),
  delete: vi.fn((n: string) => { delete jar[n]; }),
  get: (n: string) => (n in jar ? { value: jar[n] } : undefined),
};
vi.mock('next/headers', () => ({ cookies: async () => cookieStore }));

const OLD_ENV = { ...process.env };
beforeEach(() => {
  for (const k of Object.keys(jar)) delete jar[k];
  vi.clearAllMocks();
  process.env = { ...OLD_ENV, KAKAO_REDIRECT_URI: 'http://localhost:3000/api/bff/oauth/kakao/callback' };
});
afterEach(() => { process.env = { ...OLD_ENV }; vi.unstubAllGlobals(); });

function beJson(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}
function locationOf(res: Response) { return new URL(res.headers.get('location')!).pathname + new URL(res.headers.get('location')!).search; }

describe('GET /api/bff/oauth/kakao/callback', () => {
  it('state 통과 + BE 성공 → 인증 쿠키 설정 후 /dashboard', async () => {
    jar['oauth_state'] = 'S';
    vi.stubGlobal('fetch', vi.fn(async () => beJson(
      { success: true, data: { accessToken: 'A', refreshToken: 'R' }, error: null }, 200)));
    const { GET } = await import('@/app/api/bff/oauth/kakao/callback/route');

    const res = await GET(new Request('http://localhost:3000/api/bff/oauth/kakao/callback?code=c&state=S'));

    expect(locationOf(res)).toBe('/dashboard');
    expect(jar['access_token']).toBe('A');
    expect(jar['refresh_token']).toBe('R');
    expect(jar['oauth_state']).toBeUndefined(); // 단일 사용
  });

  it('state 불일치 → /login?error=state, BE 미호출', async () => {
    jar['oauth_state'] = 'S';
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const { GET } = await import('@/app/api/bff/oauth/kakao/callback/route');

    const res = await GET(new Request('http://localhost:3000/api/bff/oauth/kakao/callback?code=c&state=WRONG'));

    expect(locationOf(res)).toBe('/login?error=state');
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar['oauth_state']).toBeUndefined();
    expect(jar['access_token']).toBeUndefined();
  });

  it('카카오 error 파라미터 → /login?error=kakao_cancelled', async () => {
    jar['oauth_state'] = 'S';
    const { GET } = await import('@/app/api/bff/oauth/kakao/callback/route');

    const res = await GET(new Request('http://localhost:3000/api/bff/oauth/kakao/callback?error=access_denied&state=S'));

    expect(locationOf(res)).toBe('/login?error=kakao_cancelled');
    expect(jar['oauth_state']).toBeUndefined();
  });

  it('BE 교환 실패 → /login?error=oauth', async () => {
    jar['oauth_state'] = 'S';
    vi.stubGlobal('fetch', vi.fn(async () => beJson(
      { success: false, data: null, error: { resultCode: '502-01', code: 'OAUTH_PROVIDER_ERROR', message: '실패' } }, 502)));
    const { GET } = await import('@/app/api/bff/oauth/kakao/callback/route');

    const res = await GET(new Request('http://localhost:3000/api/bff/oauth/kakao/callback?code=c&state=S'));

    expect(locationOf(res)).toBe('/login?error=oauth');
    expect(jar['access_token']).toBeUndefined();
    expect(jar['oauth_state']).toBeUndefined();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — callback 라우트 없음.

- [ ] **Step 3: 구현**

`FE/app/api/bff/oauth/kakao/start/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { issueState } from '@/lib/server/oauthState';
import { buildAuthorizeUrl } from '@/lib/server/kakao';

export async function GET(request: Request) {
  const origin = new URL(request.url).origin;
  if (!process.env.KAKAO_CLIENT_ID) {
    return NextResponse.redirect(new URL('/login?error=oauth_config', origin));
  }
  const state = issueState(await cookies());
  return NextResponse.redirect(buildAuthorizeUrl(state));
}
```

`FE/app/api/bff/oauth/kakao/callback/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { verifyState, clearState } from '@/lib/server/oauthState';
import { exchangeCode } from '@/lib/server/kakao';
import { setAuthCookies } from '@/lib/server/cookies';

export async function GET(request: Request) {
  const url = new URL(request.url);
  const origin = url.origin;
  const store = await cookies();
  const redirect = (path: string) => NextResponse.redirect(new URL(path, origin));

  const kakaoError = url.searchParams.get('error');
  const code = url.searchParams.get('code');
  const urlState = url.searchParams.get('state');

  const stateOk = verifyState(store, urlState);
  clearState(store); // 단일 사용: 어느 경로로 끝나든 삭제

  if (kakaoError) return redirect('/login?error=kakao_cancelled');
  if (!stateOk) return redirect('/login?error=state');
  if (!code) return redirect('/login?error=oauth');

  const res = await exchangeCode(code, process.env.KAKAO_REDIRECT_URI ?? '');
  if (!res.ok) return redirect('/login?error=oauth');

  const { accessToken, refreshToken } = res.data as { accessToken: string; refreshToken: string };
  setAuthCookies(store, accessToken, refreshToken);
  return redirect('/dashboard');
}
```

`FE/.env.local.example` 맨 끝에 추가:

```
# 카카오 소셜 로그인 (서버 전용, NEXT_PUBLIC_ 금지)
KAKAO_CLIENT_ID=
KAKAO_REDIRECT_URI=http://localhost:3000/api/bff/oauth/kakao/callback
```

- [ ] **Step 4: 테스트 통과 + 빌드 확인**

```bash
npm --prefix FE test
npm --prefix FE run build
```

Expected: 테스트 PASS / 빌드 성공(라우트 타입 검증 포함).

- [ ] **Step 5: 커밋**

```bash
git add FE/app/api/bff/oauth/kakao FE/.env.local.example FE/__tests__/kakao-callback.test.ts
git commit -m "feat: 카카오 OAuth start/callback BFF 라우트 및 env 추가"
```

---

## Task 3: /login 카카오 버튼 + error 메시지 (서버 페이지 + 클라이언트 폼 분리)

기존 `/login`은 클라이언트 컴포넌트라 `searchParams.error`를 직접 못 읽는다. 서버 페이지가 error를 읽어 메시지로 변환해 클라이언트 폼에 주입한다.

**Files:**
- Modify: `FE/app/(auth)/login/page.tsx` (서버 컴포넌트로 전환)
- Create: `FE/app/(auth)/login/LoginForm.tsx` (기존 폼 로직 이동 + 카카오 버튼)
- Test: `FE/__tests__/login-page.test.tsx` (수정 — LoginForm 대상)

**Interfaces:**
- Consumes: `postBff`(lib/api.ts), `useRouter`(next/navigation)
- Produces: `LoginForm({ initialError }: { initialError: string | null })`; page.tsx가 `ERROR_MESSAGES` export

- [ ] **Step 1: 테스트 수정(실패 유도)**

기존 `FE/__tests__/login-page.test.tsx`를 아래로 교체한다(대상이 LoginPage→LoginForm으로 바뀜):

```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import { LoginForm } from '@/app/(auth)/login/LoginForm';
import { ERROR_MESSAGES } from '@/app/(auth)/login/page';

describe('LoginForm', () => {
  it('아이디/비밀번호 입력과 로그인 버튼을 렌더링한다', () => {
    render(<LoginForm initialError={null} />);
    expect(screen.getByLabelText('아이디')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });

  it('카카오 로그인 링크가 start 라우트를 가리킨다', () => {
    render(<LoginForm initialError={null} />);
    const kakao = screen.getByRole('link', { name: '카카오 로그인' });
    expect(kakao).toHaveAttribute('href', '/api/bff/oauth/kakao/start');
  });

  it('initialError를 화면에 표시한다', () => {
    render(<LoginForm initialError="카카오 로그인이 취소되었습니다." />);
    expect(screen.getByText('카카오 로그인이 취소되었습니다.')).toBeInTheDocument();
  });
});

describe('ERROR_MESSAGES', () => {
  it('알려진 error 코드에 한글 메시지를 매핑한다', () => {
    expect(ERROR_MESSAGES.state).toBeTruthy();
    expect(ERROR_MESSAGES.kakao_cancelled).toBe('카카오 로그인이 취소되었습니다.');
    expect(ERROR_MESSAGES.oauth).toBeTruthy();
    expect(ERROR_MESSAGES.oauth_config).toBeTruthy();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `LoginForm`, `ERROR_MESSAGES` 없음.

- [ ] **Step 3: 구현**

`FE/app/(auth)/login/LoginForm.tsx` (기존 page.tsx 폼 로직을 옮기고 initialError·카카오 버튼 추가):

```tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postBff } from '@/lib/api';

export function LoginForm({ initialError }: { initialError: string | null }) {
  const router = useRouter();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(initialError);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPending(true);
    setError(null);
    const res = await postBff('/api/bff/login', { loginId, password });
    setPending(false);
    if (res.ok) router.push('/dashboard');
    else setError(res.message ?? '로그인에 실패했습니다.');
  }

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="mb-6 text-2xl font-bold">로그인</h1>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          아이디
          <input className="rounded border px-3 py-2" value={loginId}
            onChange={(e) => setLoginId(e.target.value)} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          비밀번호
          <input type="password" className="rounded border px-3 py-2" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={pending}
          className="rounded bg-black py-2 text-white disabled:opacity-50">로그인</button>
      </form>
      <a href="/api/bff/oauth/kakao/start"
        className="mt-3 block rounded bg-[#FEE500] py-2 text-center text-sm font-medium text-black">카카오 로그인</a>
      <p className="mt-4 text-sm">
        계정이 없으신가요? <a href="/signup" className="underline">회원가입</a>
      </p>
    </main>
  );
}
```

`FE/app/(auth)/login/page.tsx` (서버 컴포넌트로 교체):

```tsx
import { LoginForm } from './LoginForm';

/** OAuth/로그인 실패 코드 → 사용자 안내 문구. */
export const ERROR_MESSAGES: Record<string, string> = {
  state: '보안 검증에 실패했습니다. 다시 시도해 주세요.',
  kakao_cancelled: '카카오 로그인이 취소되었습니다.',
  oauth: '카카오 로그인에 실패했습니다.',
  oauth_config: '카카오 로그인이 현재 설정되지 않았습니다.',
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  const initialError = error ? (ERROR_MESSAGES[error] ?? '로그인에 실패했습니다.') : null;
  return <LoginForm initialError={initialError} />;
}
```

- [ ] **Step 4: 테스트 통과 + 빌드 확인**

```bash
npm --prefix FE test
npm --prefix FE run build
```

Expected: 테스트 PASS / 빌드 성공.

- [ ] **Step 5: 커밋**

```bash
git add "FE/app/(auth)/login" FE/__tests__/login-page.test.tsx
git commit -m "feat: 로그인 화면에 카카오 버튼과 OAuth 에러 안내 추가"
```

---

## Task 4: 문서 반영 + 로컬 배선 검증

**Files:**
- Modify: `docs/ARCHITECTURE-STATUTE.md`, `docs/CONTEXT.md`, `docs/TODO-BACKLOG.md`, `docs/AI-ACTION-LOGS.md`, `docs/AI-MAJOR-EVENT.md`, `docs/AI-MAJOR-EVENT-RECAP.md`

- [ ] **Step 1: `ARCHITECTURE-STATUTE.md` FE 절 보강**

FE 절의 BFF 설명 하위 항목에 한 줄 추가(BFF 계층 설명 근처):

```markdown
  * 소셜 로그인: 카카오는 서버 라우트 `/api/bff/oauth/kakao/start`(state 발급→카카오 302)와 `/api/bff/oauth/kakao/callback`(state 대조→BE 코드교환→쿠키)로 처리. CSRF `state`는 서버 생성·httpOnly 쿠키(`oauth_state`)·단일사용. `KAKAO_CLIENT_ID`/`KAKAO_REDIRECT_URI`는 서버 env.
```

- [ ] **Step 2: `CONTEXT.md` 주의 추가**

`## 주의`의 FE 인증 항목 뒤에 추가:

```markdown
* FE 카카오 로그인: `/login` 버튼→`/api/bff/oauth/kakao/start`(CSRF state httpOnly 쿠키+카카오 302)→카카오→`/api/bff/oauth/kakao/callback`(state 대조→BE `/api/auth/oauth/kakao` 교환→인증쿠키→/dashboard). 에러는 `/login?error=`. `KAKAO_CLIENT_ID`/`KAKAO_REDIRECT_URI` 서버 env. **실제 카카오 왕복은 앱 키 미확보로 미검증**(배선만 목/로컬 확인).
```

- [ ] **Step 3: `TODO-BACKLOG.md` 갱신**

`* [ ] FE: 카카오 소셜 로그인 연동(인가코드 리다이렉트 → BFF 교환)` 항목을 다음으로 교체:

```markdown
* [ ] FE: 카카오 실제 로그인 왕복 수동 검증 — 앱 키(REST API 키)·redirect_uri 등록 후. FE 코드·CSRF state는 2026-07-15 구현 완료(배선 목/로컬 검증), 실키 성공만 잔여.
```

- [ ] **Step 4: 기록 문서 갱신**

`AI-ACTION-LOGS.md` 맨 아래에 추가:

```markdown
* 2026-07-15 — FE 카카오 소셜 로그인 연동 TDD 구현(브랜치 feature/fe-kakao-oauth). 서버 start/callback BFF 라우트로 완전 서버측 처리(콜백은 라우트 핸들러), CSRF state 서버 생성·httpOnly 쿠키·단일사용 대조. `/login` 카카오 버튼+에러 안내(서버 페이지+클라이언트 폼 분리). `oauthState`/`kakao` 헬퍼. Vitest 목 테스트 통과, 실제 카카오 왕복은 앱 키 미확보로 배선만 로컬 검증(BE 카카오와 동일 전략).
```

`AI-MAJOR-EVENT.md` 맨 아래에 추가:

```markdown
## 2026-07-15 — FE 카카오 소셜 로그인(BFF + CSRF state) 확정

### 주요 의사결정
* **완전 서버측 콜백**: 카카오 `redirect_uri`를 서버 GET 라우트(`/api/bff/oauth/kakao/callback`)로 직접 지정해, code·state·BE교환·쿠키가 전부 서버에서 처리되고 토큰이 UI에 노출되지 않게 함. 별도 콜백 페이지 컴포넌트 없음.
* **CSRF state**: authorize 시작을 서버 라우트(`/start`)로 두고 state를 서버 생성→httpOnly 쿠키(`oauth_state`, 10분, 단일사용)→콜백에서 대조. client_id를 클라이언트에 노출하지 않는 A안 채택(기존 BFF 일관성 + state 확장 용이).
* **미검증 범위**: 카카오 앱 키 미확보로 실제 왕복은 수동 검증 보류. 배선(302·state·BE도달)은 목/로컬로 확인. (BE 카카오 2026-07-13과 동일 전략)
```

`AI-MAJOR-EVENT-RECAP.md` 맨 아래에 추가:

```markdown
* **2026-07-15 FE 카카오 소셜 로그인**: 서버 start/callback BFF 라우트 + CSRF state(httpOnly 쿠키, 단일사용).
  * 콜백을 서버 라우트로 둬 토큰이 UI에 안 닿음. client_id는 서버 env(NEXT_PUBLIC_ 아님)
  * 실제 카카오 왕복은 앱 키 확보 후 수동 검증(코드·배선은 완료)
```

- [ ] **Step 5: 로컬 배선 검증(가능 범위) 후 최종 빌드·커밋**

BE와 FE를 띄울 수 있으면 배선을 확인한다(불가하면 보고서에 "미수행"으로 남긴다):

```bash
# BE: docker compose up -d 후 gradlew bootRun (필요 시 --args=--spring.datasource.password=... override)
# FE: cp FE/.env.local.example FE/.env.local (KAKAO_CLIENT_ID는 비워둠) 후 npm --prefix FE run dev
```

- `/api/bff/oauth/kakao/start` 접근 → `KAKAO_CLIENT_ID` 비어 있으므로 `/login?error=oauth_config`로 리다이렉트되는지 확인(설정 누락 방어).
- state 불일치 콜백 → `/login?error=state` 확인.

```bash
npm --prefix FE run build
git add docs
git commit -m "docs: FE 카카오 소셜 로그인(BFF+CSRF state) 반영 및 결정 기록"
```

---

## 완료 후 남는 것 (플랜 범위 밖)

- **노션 동기화** — 컨트롤러(메인 세션)가 병합 후 처리.
- **실제 카카오 왕복 수동 검증** — 앱 키·redirect_uri 등록 후.
- 구글 등 타 provider FE, 소셜 계정 연결/해제 관리.
