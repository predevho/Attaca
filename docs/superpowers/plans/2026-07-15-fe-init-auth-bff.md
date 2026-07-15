# FE(Next.js) 초기화 + 인증 플로우(BFF) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `FE/`에 Next.js를 세우고, BFF(httpOnly 쿠키) 패턴으로 BE 인증 API와 연동해 회원가입·로그인·보호된 대시보드 플로우를 완성한다.

**Architecture:** 브라우저는 Next.js(same-origin)하고만 통신하고, Next 서버가 Spring을 서버 간 호출한다. 토큰 로직은 `lib/server/*`(순수 함수)에 모아 단위 테스트하고, 라우트 핸들러는 쿠키 저장소를 주입받아 위임하는 얇은 글루로 둔다. UI는 토큰을 만지지 않고 `/api/bff/*`만 fetch한다.

**Tech Stack:** Next.js 16(App Router) / React 19 / TypeScript / Tailwind CSS v4 / Vitest 4 + Testing Library / 네이티브 fetch

**설계 문서:** `docs/superpowers/specs/2026-07-15-fe-init-auth-bff-design.md`

## Global Constraints

- 위치 `FE/`(레포 루트 하위). BE와 독립 실행. 패키지 매니저 **npm**.
- 통신은 **네이티브 fetch만** 사용. axios·React Query 등 데이터 페칭 라이브러리 도입 금지(스펙 §2 결정).
- **토큰은 UI(클라이언트 컴포넌트)에서 절대 접근하지 않는다.** 쿠키 R/W는 라우트 핸들러/서버 코드에서만.
- 서버 전용 파일(`lib/server/*`)은 최상단에 `import 'server-only';`.
- 쿠키 옵션: `httpOnly: true`, `sameSite: 'lax'`, `secure: process.env.NODE_ENV === 'production'`, `path: '/'`. access `maxAge` 1800초, refresh `maxAge` 1209600초.
- BE 응답은 `ApiResponse{success, data, error{resultCode, code, message}}`. 언랩 후 `error.message`만 UI 노출.
- `BE_BASE_URL`은 **서버 전용 env**(`NEXT_PUBLIC_` 접두사 금지). 기본값 `http://localhost:8080`.
- 각 태스크 끝에서 `npm --prefix FE run build`(타입체크 포함)와 `npm --prefix FE test`가 통과해야 한다.
- 커밋 메시지는 한글, `유형: 한글 요약`.
- 실제 BE 연동(가입→로그인→대시보드)은 수동 검증이며 자동 테스트에 넣지 않는다.

---

## 스펙과의 차이 (의도적)

스펙 §범위는 "프로필 화면"을 범위 밖으로 두면서 §5-4에 "401 시 reissue 1회 재시도"를 범위 안에 둔다. 그런데 인증이 필요한 BE 호출이 하나도 없으면 reissue 로직에 **실사용 호출자가 없어** 죽은 코드가 된다. 이를 해소하기 위해 이 계획은 대시보드가 인증 프로브로 **`GET /api/members/me/profile`을 BFF(`/api/bff/me`) 경유로 1회 호출**한다. 이는 프로필 *화면*(폼·수정·이미지)을 만드는 것이 아니라, "로그인 확인용 페이지"(스펙 §5-3)를 정적 문자열 대신 **실제 인증 호출로 확인**하는 것이며, 그 부수효과로 reissue 재시도 경로가 end-to-end로 검증된다.

---

## File Structure

```
FE/
├── app/
│   ├── (auth)/login/page.tsx        # 로그인 폼(클라이언트)
│   ├── (auth)/signup/page.tsx       # 회원가입 폼(클라이언트)
│   ├── dashboard/page.tsx           # 보호 페이지: /api/bff/me 호출 + 로그아웃
│   ├── api/bff/signup/route.ts      # → BE signup
│   ├── api/bff/login/route.ts       # → BE login, 쿠키 설정
│   ├── api/bff/logout/route.ts      # 쿠키 삭제
│   ├── api/bff/me/route.ts          # → BE me/profile (인증, reissue 재시도)
│   ├── layout.tsx / globals.css     # create-next-app 기본
│   └── page.tsx                     # 루트 → /dashboard 리다이렉트
├── lib/
│   ├── unwrap.ts                    # ApiResponse 언랩(순수)
│   ├── server/beClient.ts           # 'server-only' fetch→Spring, 정규화
│   ├── server/cookies.ts            # 쿠키 이름·옵션·set/clear(저장소 주입)
│   ├── server/session.ts            # 인증 호출 + reissue 1회 재시도
│   └── api.ts                       # 클라이언트 fetch('/api/bff/*') 래퍼
├── middleware.ts                    # /dashboard 보호(쿠키 존재 검사)
├── __tests__/*.test.ts(x)           # Vitest
├── vitest.config.ts / vitest.setup.ts
├── .env.local.example               # BE_BASE_URL=http://localhost:8080
└── (create-next-app 기본 파일)
```

---

## Task 1: FE 스캐폴딩 + Vitest 하네스

`FE/`에 Next.js를 세우고 Vitest를 붙여, 빌드와 테스트가 도는 빈 하네스를 만든다. (아래 버전·명령은 스크래치패드 프로브로 실제 검증됨: Next 16.2.10 / React 19 / Vitest 4.1.10)

**Files:**
- Create: `FE/**`(create-next-app 산출물)
- Create: `FE/vitest.config.ts`, `FE/vitest.setup.ts`, `FE/.env.local.example`, `FE/__tests__/harness.test.ts`
- Modify: `FE/package.json`(test 스크립트), `FE/.gitignore`(.env.local.example 예외)
- Delete: `FE/CLAUDE.md`, `FE/AGENTS.md`(create-next-app@16 생성물 — 레포 루트 CLAUDE.md가 정본)

**Interfaces:**
- Consumes: 없음
- Produces: `@/*` → `FE/*` 경로 별칭, `npm --prefix FE test`(Vitest), `npm --prefix FE run build`

- [ ] **Step 1: create-next-app 실행 (레포 루트에서)**

```bash
cd /c/Users/dlagu/Desktop/CS/Attaca
npx --yes create-next-app@latest FE --ts --tailwind --eslint --app --src-dir=false --import-alias "@/*" --use-npm --no-turbopack --disable-git
```

Expected: `Success! Created FE at ...`. `FE/package.json`에 next 16.x/react 19.x 생성.

- [ ] **Step 2: 불필요 생성물 제거 + Vitest 의존성 설치**

```bash
rm -f FE/CLAUDE.md FE/AGENTS.md
npm --prefix FE install -D vitest@^4 @vitejs/plugin-react@^6 jsdom@^29 @testing-library/react@^16 @testing-library/jest-dom@^6 @testing-library/user-event@^14
```

- [ ] **Step 3: Vitest 설정 작성**

`FE/vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./', import.meta.url)) },
  },
});
```

`FE/vitest.setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
```

`FE/package.json`의 `"scripts"`에 `test`를 추가한다(기존 dev/build/start/lint 유지):

```json
    "test": "vitest run"
```

- [ ] **Step 4: env 예시 + gitignore 예외**

`FE/.env.local.example`:

```
# BFF(Next 서버)가 Spring을 호출할 주소. 서버 전용(NEXT_PUBLIC_ 금지).
BE_BASE_URL=http://localhost:8080
```

`FE/.gitignore`의 `.env*` 줄 **바로 아래**에 예외를 추가한다(예시는 커밋해야 하므로):

```
!.env.local.example
```

- [ ] **Step 5: 하네스 테스트 작성**

`FE/__tests__/harness.test.ts`:

```ts
import { describe, it, expect } from 'vitest';

describe('harness', () => {
  it('vitest가 동작한다', () => {
    expect(1 + 1).toBe(2);
  });
});
```

- [ ] **Step 6: 빌드·테스트 확인**

```bash
npm --prefix FE test
npm --prefix FE run build
```

Expected: 테스트 1 passed / `✓ Compiled successfully` (빌드 성공).

- [ ] **Step 7: 커밋**

```bash
git add FE .gitignore
git commit -m "feat: FE Next.js 스캐폴딩 + Vitest 하네스 구성"
```

> 참고: `FE/.gitignore`가 node_modules/.next/.env*를 무시하므로 `git add FE`는 소스만 담는다. `.env.local.example`은 Step 4 예외로 포함된다.

---

## Task 2: 핵심 순수 로직 — unwrap + cookies + beClient

토큰·응답 처리의 순수 로직을 만든다. 라우트 핸들러가 아니라 여기에 로직을 모아 단위 테스트한다.

**Files:**
- Create: `FE/lib/unwrap.ts`, `FE/lib/server/cookies.ts`, `FE/lib/server/beClient.ts`
- Test: `FE/__tests__/unwrap.test.ts`, `FE/__tests__/cookies.test.ts`, `FE/__tests__/beClient.test.ts`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `unwrap<T>(res: ApiResponse<T>) → { ok, data, message }`; 타입 `ApiResponse<T>`
  - `cookies.ts`: 상수 `ACCESS_COOKIE='access_token'` / `REFRESH_COOKIE='refresh_token'`; 타입 `CookieStore = { set(name,value,opts): void; delete(name): void; get(name): {value:string}|undefined }`; `setAuthCookies(store, access, refresh)`, `setAccessCookie(store, access)`, `clearAuthCookies(store)`
  - `beClient.ts`: `beFetch(path, init?) → Promise<BeResult>`; 타입 `BeResult = { ok: boolean; status: number; data: unknown; message: string | null }`

- [ ] **Step 1: 실패 테스트 작성**

`FE/__tests__/unwrap.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { unwrap } from '@/lib/unwrap';

describe('unwrap', () => {
  it('성공 응답을 벗긴다', () => {
    expect(unwrap({ success: true, data: { id: 1 }, error: null }))
      .toEqual({ ok: true, data: { id: 1 }, message: null });
  });

  it('실패 응답에서 메시지를 뽑는다', () => {
    expect(unwrap({
      success: false, data: null,
      error: { resultCode: '401-07', code: 'LOGIN_FAILED', message: '아이디 또는 비밀번호가 올바르지 않습니다.' },
    })).toEqual({ ok: false, data: null, message: '아이디 또는 비밀번호가 올바르지 않습니다.' });
  });
});
```

`FE/__tests__/cookies.test.ts`:

```ts
import { describe, it, expect, vi } from 'vitest';
import { ACCESS_COOKIE, REFRESH_COOKIE, setAuthCookies, clearAuthCookies, type CookieStore } from '@/lib/server/cookies';

function fakeStore(): CookieStore & { jar: Record<string, string> } {
  const jar: Record<string, string> = {};
  return {
    jar,
    set: vi.fn((name: string, value: string) => { jar[name] = value; }),
    delete: vi.fn((name: string) => { delete jar[name]; }),
    get: (name: string) => (name in jar ? { value: jar[name] } : undefined),
  };
}

describe('cookies', () => {
  it('access/refresh 쿠키를 httpOnly로 설정한다', () => {
    const store = fakeStore();
    setAuthCookies(store, 'A', 'R');

    expect(store.jar[ACCESS_COOKIE]).toBe('A');
    expect(store.jar[REFRESH_COOKIE]).toBe('R');
    const opts = (store.set as any).mock.calls[0][2];
    expect(opts).toMatchObject({ httpOnly: true, sameSite: 'lax', path: '/', maxAge: 1800 });
    const refreshOpts = (store.set as any).mock.calls[1][2];
    expect(refreshOpts.maxAge).toBe(1209600);
  });

  it('두 쿠키를 모두 삭제한다', () => {
    const store = fakeStore();
    setAuthCookies(store, 'A', 'R');
    clearAuthCookies(store);

    expect(store.jar[ACCESS_COOKIE]).toBeUndefined();
    expect(store.jar[REFRESH_COOKIE]).toBeUndefined();
    expect(store.delete).toHaveBeenCalledWith(ACCESS_COOKIE);
    expect(store.delete).toHaveBeenCalledWith(REFRESH_COOKIE);
  });
});
```

`FE/__tests__/beClient.test.ts`:

```ts
import { describe, it, expect, vi, afterEach } from 'vitest';
import { beFetch } from '@/lib/server/beClient';

afterEach(() => { vi.unstubAllGlobals(); });

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('beFetch', () => {
  it('성공 ApiResponse를 정규화한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ success: true, data: { accessToken: 'A' }, error: null })));

    const res = await beFetch('/api/auth/login', { method: 'POST' });

    expect(res).toEqual({ ok: true, status: 200, data: { accessToken: 'A' }, message: null });
  });

  it('실패 ApiResponse의 메시지와 status를 보존한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(
      { success: false, data: null, error: { resultCode: '401-07', code: 'LOGIN_FAILED', message: '아이디 또는 비밀번호가 올바르지 않습니다.' } },
      401,
    )));

    const res = await beFetch('/api/auth/login', { method: 'POST' });

    expect(res.ok).toBe(false);
    expect(res.status).toBe(401);
    expect(res.message).toBe('아이디 또는 비밀번호가 올바르지 않습니다.');
  });

  it('네트워크 오류를 status 0으로 정규화한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('down'); }));

    const res = await beFetch('/api/auth/login', { method: 'POST' });

    expect(res).toEqual({ ok: false, status: 0, data: null, message: '서버에 연결할 수 없습니다.' });
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `@/lib/unwrap` 등 모듈 없음.

- [ ] **Step 3: 구현**

`FE/lib/unwrap.ts`:

```ts
export type ApiError = { resultCode: string; code: string; message: string };
export type ApiResponse<T> = { success: boolean; data: T | null; error: ApiError | null };

export function unwrap<T>(res: ApiResponse<T>): { ok: boolean; data: T | null; message: string | null } {
  if (res.success) return { ok: true, data: res.data, message: null };
  return { ok: false, data: null, message: res.error?.message ?? '알 수 없는 오류가 발생했습니다.' };
}
```

`FE/lib/server/cookies.ts`:

```ts
import 'server-only';

export const ACCESS_COOKIE = 'access_token';
export const REFRESH_COOKIE = 'refresh_token';

const ACCESS_MAX_AGE = 1800;      // 30분(BE access 만료와 일치)
const REFRESH_MAX_AGE = 1209600;  // 14일(BE refresh 만료와 일치)

/** next/headers의 cookies()가 반환하는 저장소의 최소 인터페이스(테스트를 위해 주입 가능하게 둔다). */
export type CookieStore = {
  set(name: string, value: string, options: Record<string, unknown>): void;
  delete(name: string): void;
  get(name: string): { value: string } | undefined;
};

function baseOptions(maxAge: number) {
  return {
    httpOnly: true,
    sameSite: 'lax' as const,
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge,
  };
}

export function setAccessCookie(store: CookieStore, access: string): void {
  store.set(ACCESS_COOKIE, access, baseOptions(ACCESS_MAX_AGE));
}

export function setAuthCookies(store: CookieStore, access: string, refresh: string): void {
  setAccessCookie(store, access);
  store.set(REFRESH_COOKIE, refresh, baseOptions(REFRESH_MAX_AGE));
}

export function clearAuthCookies(store: CookieStore): void {
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
}
```

`FE/lib/server/beClient.ts`:

```ts
import 'server-only';
import { unwrap, type ApiResponse } from '@/lib/unwrap';

const BE_BASE_URL = process.env.BE_BASE_URL ?? 'http://localhost:8080';

export type BeResult = { ok: boolean; status: number; data: unknown; message: string | null };

/** Spring BE를 호출하고 ApiResponse를 {ok,status,data,message}로 정규화한다. 쿠키/next 헤더를 모른다(순수 fetch). */
export async function beFetch(path: string, init?: RequestInit): Promise<BeResult> {
  let res: Response;
  try {
    res = await fetch(BE_BASE_URL + path, {
      ...init,
      headers: { 'content-type': 'application/json', ...(init?.headers ?? {}) },
    });
  } catch {
    return { ok: false, status: 0, data: null, message: '서버에 연결할 수 없습니다.' };
  }

  let body: ApiResponse<unknown>;
  try {
    body = (await res.json()) as ApiResponse<unknown>;
  } catch {
    return { ok: false, status: res.status, data: null, message: '서버 응답을 해석할 수 없습니다.' };
  }

  const { ok, data, message } = unwrap(body);
  return { ok, status: res.status, data, message };
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
npm --prefix FE test
```

Expected: PASS (7개).

- [ ] **Step 5: 커밋**

```bash
git add FE/lib FE/__tests__
git commit -m "feat: FE 코어 로직 추가 - ApiResponse 언랩·쿠키 헬퍼·BE 클라이언트"
```

---

## Task 3: 인증 호출 + reissue 1회 재시도 (session.ts)

인증이 필요한 BE 호출을 감싸, 401이면 refresh로 reissue한 뒤 access 쿠키를 갱신하고 원 요청을 정확히 1회 재시도한다.

**Files:**
- Create: `FE/lib/server/session.ts`
- Test: `FE/__tests__/session.test.ts`

**Interfaces:**
- Consumes: `beFetch`(Task 2), `cookies.ts`의 `ACCESS_COOKIE`/`REFRESH_COOKIE`/`setAccessCookie`/`clearAuthCookies`/`CookieStore`
- Produces: `authedBeFetch(store: CookieStore, path: string, init?: RequestInit) → Promise<BeResult>`

- [ ] **Step 1: 실패 테스트 작성**

`FE/__tests__/session.test.ts`:

```ts
import { describe, it, expect, vi, afterEach } from 'vitest';
import { authedBeFetch } from '@/lib/server/session';
import { ACCESS_COOKIE, REFRESH_COOKIE, type CookieStore } from '@/lib/server/cookies';

afterEach(() => { vi.unstubAllGlobals(); });

function fakeStore(access: string | null, refresh: string | null): CookieStore & { jar: Record<string, string> } {
  const jar: Record<string, string> = {};
  if (access) jar[ACCESS_COOKIE] = access;
  if (refresh) jar[REFRESH_COOKIE] = refresh;
  return {
    jar,
    set: vi.fn((n: string, v: string) => { jar[n] = v; }),
    delete: vi.fn((n: string) => { delete jar[n]; }),
    get: (n: string) => (n in jar ? { value: jar[n] } : undefined),
  };
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('authedBeFetch', () => {
  it('access가 유효하면 Bearer로 호출하고 결과를 반환한다', async () => {
    const store = fakeStore('good-access', 'refresh');
    const fetchMock = vi.fn(async () => json({ success: true, data: { hi: 1 }, error: null }, 200));
    vi.stubGlobal('fetch', fetchMock);

    const res = await authedBeFetch(store, '/api/members/me/profile');

    expect(res.ok).toBe(true);
    const authHeader = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(authHeader.Authorization).toBe('Bearer good-access');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('401이면 reissue 후 새 access로 1회 재시도한다', async () => {
    const store = fakeStore('expired', 'good-refresh');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ success: false, data: null, error: { resultCode: '401-04', code: 'EXPIRED_TOKEN', message: '만료' } }, 401))
      .mockResolvedValueOnce(json({ success: true, data: { accessToken: 'new-access' }, error: null }, 200)) // reissue
      .mockResolvedValueOnce(json({ success: true, data: { hi: 2 }, error: null }, 200));                    // retry
    vi.stubGlobal('fetch', fetchMock);

    const res = await authedBeFetch(store, '/api/members/me/profile');

    expect(res.ok).toBe(true);
    expect(store.jar[ACCESS_COOKIE]).toBe('new-access');       // 쿠키 갱신
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const retryHeader = (fetchMock.mock.calls[2][1] as RequestInit).headers as Record<string, string>;
    expect(retryHeader.Authorization).toBe('Bearer new-access');
  });

  it('reissue도 실패하면 쿠키를 지우고 401을 반환한다', async () => {
    const store = fakeStore('expired', 'bad-refresh');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ success: false, data: null, error: { resultCode: '401-04', code: 'EXPIRED_TOKEN', message: '만료' } }, 401))
      .mockResolvedValueOnce(json({ success: false, data: null, error: { resultCode: '401-04', code: 'EXPIRED_TOKEN', message: '만료' } }, 401)); // reissue 실패
    vi.stubGlobal('fetch', fetchMock);

    const res = await authedBeFetch(store, '/api/members/me/profile');

    expect(res.status).toBe(401);
    expect(store.jar[ACCESS_COOKIE]).toBeUndefined();
    expect(store.jar[REFRESH_COOKIE]).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(2); // 원요청 + reissue, 재시도 없음
  });

  it('access 쿠키가 없으면 401을 반환한다(호출 안 함)', async () => {
    const store = fakeStore(null, null);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await authedBeFetch(store, '/api/members/me/profile');

    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `@/lib/server/session` 없음.

- [ ] **Step 3: 구현**

`FE/lib/server/session.ts`:

```ts
import 'server-only';
import { beFetch, type BeResult } from '@/lib/server/beClient';
import {
  ACCESS_COOKIE, REFRESH_COOKIE,
  setAccessCookie, clearAuthCookies, type CookieStore,
} from '@/lib/server/cookies';

const UNAUTHENTICATED: BeResult = { ok: false, status: 401, data: null, message: '로그인이 필요합니다.' };

function withBearer(init: RequestInit | undefined, access: string): RequestInit {
  return { ...init, headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${access}` } };
}

/**
 * 인증이 필요한 BE 호출. access 쿠키로 호출하고, 401이면 refresh로 reissue한 뒤
 * 새 access를 쿠키에 갱신하고 원 요청을 정확히 1회 재시도한다.
 * reissue가 실패하면 쿠키를 삭제하고 401을 반환한다.
 */
export async function authedBeFetch(store: CookieStore, path: string, init?: RequestInit): Promise<BeResult> {
  const access = store.get(ACCESS_COOKIE)?.value;
  if (!access) return UNAUTHENTICATED;

  const first = await beFetch(path, withBearer(init, access));
  if (first.status !== 401) return first;

  const refresh = store.get(REFRESH_COOKIE)?.value;
  if (!refresh) {
    clearAuthCookies(store);
    return UNAUTHENTICATED;
  }

  const reissued = await beFetch('/api/auth/reissue', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: refresh }),
  });
  const newAccess = reissued.ok ? (reissued.data as { accessToken?: string })?.accessToken : undefined;
  if (!reissued.ok || !newAccess) {
    clearAuthCookies(store);
    return UNAUTHENTICATED;
  }

  setAccessCookie(store, newAccess);
  return beFetch(path, withBearer(init, newAccess)); // 정확히 1회 재시도
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
npm --prefix FE test
```

Expected: PASS (11개).

- [ ] **Step 5: 커밋**

```bash
git add FE/lib/server/session.ts FE/__tests__/session.test.ts
git commit -m "feat: 인증 호출 reissue 1회 재시도 래퍼(session) 추가"
```

---

## Task 4: BFF 라우트 핸들러 (signup/login/logout/me)

브라우저가 호출할 `/api/bff/*`를 만든다. 핸들러는 body를 읽고 `cookies()` 저장소를 lib 함수에 넘기는 얇은 글루다.

**Files:**
- Create: `FE/app/api/bff/signup/route.ts`, `FE/app/api/bff/login/route.ts`, `FE/app/api/bff/logout/route.ts`, `FE/app/api/bff/me/route.ts`
- Test: `FE/__tests__/bff-login.test.ts`

**Interfaces:**
- Consumes: `beFetch`(Task 2), `setAuthCookies`/`clearAuthCookies`(Task 2), `authedBeFetch`(Task 3), `next/headers`의 `cookies()`, `next/server`의 `NextResponse`
- Produces: BFF 엔드포인트 4종. 응답은 `{ ok, data, message }` JSON + 적절한 status.

- [ ] **Step 1: 실패 테스트 작성 (login 핸들러)**

`FE/__tests__/bff-login.test.ts`:

```ts
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';

const jar: Record<string, string> = {};
const cookieStore = {
  set: vi.fn((n: string, v: string) => { jar[n] = v; }),
  delete: vi.fn((n: string) => { delete jar[n]; }),
  get: (n: string) => (n in jar ? { value: jar[n] } : undefined),
};

vi.mock('next/headers', () => ({ cookies: async () => cookieStore }));

beforeEach(() => { for (const k of Object.keys(jar)) delete jar[k]; vi.clearAllMocks(); });
afterEach(() => { vi.unstubAllGlobals(); });

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('POST /api/bff/login', () => {
  it('로그인 성공 시 쿠키를 설정하고 ok를 반환한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(
      { success: true, data: { accessToken: 'A', refreshToken: 'R' }, error: null }, 200)));
    const { POST } = await import('@/app/api/bff/login/route');

    const req = new Request('http://localhost/api/bff/login', {
      method: 'POST', body: JSON.stringify({ loginId: 'u', password: 'p' }),
    });
    const res = await POST(req);

    expect(res.status).toBe(200);
    expect(jar['access_token']).toBe('A');
    expect(jar['refresh_token']).toBe('R');
  });

  it('로그인 실패 시 쿠키 없이 에러 메시지를 반환한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(
      { success: false, data: null, error: { resultCode: '401-07', code: 'LOGIN_FAILED', message: '아이디 또는 비밀번호가 올바르지 않습니다.' } }, 401)));
    const { POST } = await import('@/app/api/bff/login/route');

    const req = new Request('http://localhost/api/bff/login', {
      method: 'POST', body: JSON.stringify({ loginId: 'u', password: 'x' }),
    });
    const res = await POST(req);
    const bodyJson = await res.json();

    expect(res.status).toBe(401);
    expect(jar['access_token']).toBeUndefined();
    expect(bodyJson.message).toBe('아이디 또는 비밀번호가 올바르지 않습니다.');
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `@/app/api/bff/login/route` 없음.

- [ ] **Step 3: 구현**

`FE/app/api/bff/login/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { beFetch } from '@/lib/server/beClient';
import { setAuthCookies } from '@/lib/server/cookies';

export async function POST(request: Request) {
  const body = await request.text();
  const res = await beFetch('/api/auth/login', { method: 'POST', body });

  if (res.ok) {
    const { accessToken, refreshToken } = res.data as { accessToken: string; refreshToken: string };
    setAuthCookies(await cookies(), accessToken, refreshToken);
  }
  return NextResponse.json({ ok: res.ok, message: res.message }, { status: res.status || 200 });
}
```

`FE/app/api/bff/signup/route.ts`:

```ts
import { NextResponse } from 'next/server';
import { beFetch } from '@/lib/server/beClient';

export async function POST(request: Request) {
  const body = await request.text();
  const res = await beFetch('/api/auth/signup', { method: 'POST', body });
  return NextResponse.json({ ok: res.ok, message: res.message }, { status: res.status || 200 });
}
```

`FE/app/api/bff/logout/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { clearAuthCookies } from '@/lib/server/cookies';

export async function POST() {
  clearAuthCookies(await cookies());
  return NextResponse.json({ ok: true, message: null });
}
```

`FE/app/api/bff/me/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authedBeFetch } from '@/lib/server/session';

export async function GET() {
  const res = await authedBeFetch(await cookies(), '/api/members/me/profile');
  return NextResponse.json({ ok: res.ok, data: res.data, message: res.message }, { status: res.status || 200 });
}
```

- [ ] **Step 4: 테스트 통과 + 빌드 확인**

```bash
npm --prefix FE test
npm --prefix FE run build
```

Expected: 테스트 PASS(13개) / 빌드 성공.

- [ ] **Step 5: 커밋**

```bash
git add FE/app/api/bff FE/__tests__/bff-login.test.ts
git commit -m "feat: BFF 라우트 핸들러 추가 - signup/login/logout/me"
```

---

## Task 5: 미들웨어 + 클라이언트 fetch 래퍼

`/dashboard`를 쿠키 존재로 보호하고, UI가 쓸 얇은 fetch 래퍼를 만든다.

**Files:**
- Create: `FE/middleware.ts`, `FE/lib/api.ts`
- Test: `FE/__tests__/api.test.ts`

**Interfaces:**
- Consumes: `next/server`의 `NextResponse`/`NextRequest`
- Produces:
  - `middleware(req)`: access 쿠키 없으면 `/login`으로 리다이렉트. `config.matcher = ['/dashboard/:path*']`
  - `api.ts`: `postBff(path, body?) → Promise<{ ok, data, message }>`, `getBff(path) → Promise<{ ok, data, message }>`

- [ ] **Step 1: 실패 테스트 작성 (api.ts)**

`FE/__tests__/api.test.ts`:

```ts
import { describe, it, expect, vi, afterEach } from 'vitest';
import { postBff, getBff } from '@/lib/api';

afterEach(() => { vi.unstubAllGlobals(); });

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('client api', () => {
  it('postBff는 상대경로 /api/bff/*를 POST한다', async () => {
    const fetchMock = vi.fn(async () => json({ ok: true, message: null }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await postBff('/api/bff/login', { loginId: 'u', password: 'p' });

    expect(res.ok).toBe(true);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/bff/login');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('POST');
  });

  it('getBff는 상대경로를 GET하고 data를 돌려준다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ ok: true, data: { instruments: [] }, message: null })));

    const res = await getBff('/api/bff/me');

    expect(res.ok).toBe(true);
    expect(res.data).toEqual({ instruments: [] });
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `@/lib/api` 없음.

- [ ] **Step 3: 구현**

`FE/lib/api.ts`:

```ts
export type BffResult<T = unknown> = { ok: boolean; data?: T; message: string | null };

async function parse<T>(res: Response): Promise<BffResult<T>> {
  try {
    return (await res.json()) as BffResult<T>;
  } catch {
    return { ok: false, message: '응답을 해석할 수 없습니다.' };
  }
}

/** 클라이언트 컴포넌트 전용: same-origin BFF만 호출한다. 토큰은 서버 쿠키에 있으므로 여기서 다루지 않는다. */
export async function postBff<T = unknown>(path: string, body?: unknown): Promise<BffResult<T>> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parse<T>(res);
}

export async function getBff<T = unknown>(path: string): Promise<BffResult<T>> {
  const res = await fetch(path, { method: 'GET' });
  return parse<T>(res);
}
```

`FE/middleware.ts`:

```ts
import { NextResponse, type NextRequest } from 'next/server';

// 쿠키 이름을 여기서 하드코딩하는 이유: lib/server/cookies.ts는 'server-only'라 edge 런타임인
// 미들웨어에서 import할 수 없다. 이름이 바뀌면 두 곳을 함께 고칠 것(access_token).
const ACCESS_COOKIE = 'access_token';

/**
 * /dashboard 보호. access 쿠키의 '존재'만 검사한다(서명 검증은 BE가 실제 호출 시 수행).
 * 만료된 access여도 통과시키고, 데이터 호출 단계에서 reissue가 처리한다 —
 * FE에 JWT 시크릿을 두지 않기 위함.
 */
export function middleware(req: NextRequest) {
  const hasAccess = req.cookies.has(ACCESS_COOKIE);
  if (!hasAccess) {
    const loginUrl = new URL('/login', req.url);
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = { matcher: ['/dashboard/:path*'] };
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
npm --prefix FE test
```

Expected: PASS (15개).

- [ ] **Step 5: 커밋**

```bash
git add FE/middleware.ts FE/lib/api.ts FE/__tests__/api.test.ts
git commit -m "feat: /dashboard 보호 미들웨어 및 클라이언트 BFF fetch 래퍼 추가"
```

---

## Task 6: UI 화면 (회원가입/로그인/대시보드/루트)

폼과 화면을 만든다. 클라이언트 컴포넌트는 `lib/api.ts`만 쓰고 토큰을 만지지 않는다.

**Files:**
- Create: `FE/app/(auth)/login/page.tsx`, `FE/app/(auth)/signup/page.tsx`, `FE/app/dashboard/page.tsx`
- Modify: `FE/app/page.tsx`(루트 리다이렉트)
- Test: `FE/__tests__/login-page.test.tsx`

**Interfaces:**
- Consumes: `postBff`/`getBff`(Task 5), `next/navigation`의 `useRouter`, `redirect`
- Produces: 4개 라우트 화면

- [ ] **Step 1: 실패 테스트 작성 (로그인 폼 스모크)**

`FE/__tests__/login-page.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import LoginPage from '@/app/(auth)/login/page';

describe('LoginPage', () => {
  it('아이디/비밀번호 입력과 로그인 버튼을 렌더링한다', () => {
    render(<LoginPage />);
    expect(screen.getByLabelText('아이디')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — 로그인 페이지 없음.

- [ ] **Step 3: 구현**

`FE/app/(auth)/login/page.tsx`:

```tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postBff } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
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
      <p className="mt-4 text-sm">
        계정이 없으신가요? <a href="/signup" className="underline">회원가입</a>
      </p>
    </main>
  );
}
```

`FE/app/(auth)/signup/page.tsx`:

```tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postBff } from '@/lib/api';

export default function SignupPage() {
  const router = useRouter();
  const [form, setForm] = useState({ loginId: '', password: '', email: '', nickname: '' });
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function update(key: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPending(true);
    setError(null);
    const res = await postBff('/api/bff/signup', form);
    setPending(false);
    if (res.ok) router.push('/login');
    else setError(res.message ?? '회원가입에 실패했습니다.');
  }

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="mb-6 text-2xl font-bold">회원가입</h1>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">아이디
          <input className="rounded border px-3 py-2" value={form.loginId} onChange={update('loginId')} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">비밀번호
          <input type="password" className="rounded border px-3 py-2" value={form.password} onChange={update('password')} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">이메일
          <input type="email" className="rounded border px-3 py-2" value={form.email} onChange={update('email')} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">닉네임
          <input className="rounded border px-3 py-2" value={form.nickname} onChange={update('nickname')} required />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={pending}
          className="rounded bg-black py-2 text-white disabled:opacity-50">회원가입</button>
      </form>
      <p className="mt-4 text-sm">
        이미 계정이 있으신가요? <a href="/login" className="underline">로그인</a>
      </p>
    </main>
  );
}
```

`FE/app/dashboard/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getBff, postBff } from '@/lib/api';

export default function DashboardPage() {
  const router = useRouter();
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    // 인증 프로브: /api/bff/me가 성공하면 세션이 살아있는 것(내부에서 reissue 처리).
    getBff('/api/bff/me').then((res) => {
      if (res.ok) setLoaded(true);
      else router.push('/login');
    });
  }, [router]);

  async function onLogout() {
    await postBff('/api/bff/logout');
    router.push('/login');
  }

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="mb-6 text-2xl font-bold">대시보드</h1>
      <p className="mb-6">{loaded ? '로그인된 상태입니다. 환영합니다!' : '확인 중...'}</p>
      <button onClick={onLogout} className="rounded border px-4 py-2">로그아웃</button>
    </main>
  );
}
```

`FE/app/page.tsx`(기존 내용 전체 교체):

```tsx
import { redirect } from 'next/navigation';

export default function Home() {
  redirect('/dashboard');
}
```

- [ ] **Step 4: 테스트 통과 + 빌드 확인**

```bash
npm --prefix FE test
npm --prefix FE run build
```

Expected: 테스트 PASS(16개) / 빌드 성공.

- [ ] **Step 5: 커밋**

```bash
git add "FE/app"
git commit -m "feat: 인증 UI 추가 - 회원가입/로그인/대시보드/루트 리다이렉트"
```

---

## Task 7: 문서 반영 + 수동 E2E 안내

**Files:**
- Modify: `docs/ARCHITECTURE-STATUTE.md`, `docs/ARCHITECTURE-CONSTITUTION.md`, `docs/CONTEXT.md`, `docs/TODO-READY.md`, `docs/TODO-DONE.md`, `docs/TODO-BACKLOG.md`, `docs/AI-ACTION-LOGS.md`, `docs/AI-MAJOR-EVENT.md`, `docs/AI-MAJOR-EVENT-RECAP.md`

- [ ] **Step 1: `ARCHITECTURE-STATUTE.md` §1 Frontend 절 교체**

기존 Frontend 항목(`* Next.js (React)` … `* 동일 BE API를…`)을 다음으로 교체:

```markdown
### Frontend

* Next.js 16 (App Router) / React 19 / TypeScript / Tailwind CSS v4 / Vitest. 패키지 매니저 npm.
* 위치: `FE/`. BE와 독립 실행(`cd FE && npm run dev`, 기본 :3000).
* **BFF 패턴**: 브라우저는 Next(same-origin)하고만 통신하고, Next 서버가 Spring을 서버 간 호출한다.
  토큰은 httpOnly 쿠키(`access_token`/`refresh_token`)로 다루며 UI JavaScript는 토큰을 만지지 않는다.
  * 계층: `lib/server/*`(순수 로직·쿠키·reissue) → `app/api/bff/**`(라우트 핸들러 글루) → `app/**`(UI).
  * BE 호출 주소는 서버 env `BE_BASE_URL`(클라이언트 노출 금지). 통신은 네이티브 fetch(라이브러리 미도입).
* 동일 BE API를 모바일 앱이 재사용할 수 있도록 API 소비 방식을 플랫폼 독립적으로 유지한다.
```

- [ ] **Step 2: `ARCHITECTURE-CONSTITUTION.md` §2 보강**

`* FE와 BE는 REST API와 WebSocket으로만 통신한다.` 아래에 추가:

```markdown
* 웹(Next.js)은 BFF(Backend-For-Frontend)를 통해 same-origin으로 BE와 통신한다. 브라우저가 BE를 직접 호출하지 않으므로 웹 경로에는 CORS가 필요 없다. (모바일 앱 등 BE를 직접 호출하는 소비처가 생기면 그때 CORS를 도입한다.)
```

- [ ] **Step 3: `CONTEXT.md` 갱신**

`## 현재 상태`의 단계 줄을 교체:

```markdown
* 단계: BE(인증/프로필/파일/DB) + FE 인증 플로우(Next.js BFF) 완료. 다음은 MEMBER 프로필 화면 또는 카카오 소셜 FE 또는 다음 도메인.
```

`FE: Next.js (React), 위치 `FE/` (아직 비어 있음)` 줄을 교체:

```markdown
  * FE: Next.js 16(App Router)/React 19/TS/Tailwind/Vitest, 위치 `FE/`. BFF+httpOnly 쿠키. `cd FE && npm run dev`(:3000)
```

`## 주의` 맨 끝에 추가:

```markdown
* FE 인증: BFF 3계층(`lib/server/*`→`app/api/bff/**`→UI). 토큰은 httpOnly 쿠키, UI는 토큰 안 만짐. 통신은 네이티브 fetch(라이브러리 미도입). BE 주소는 서버 env `BE_BASE_URL`. `/dashboard`는 미들웨어가 쿠키 존재로 보호, reissue는 `lib/server/session.ts`가 401 시 1회 재시도. 실연동은 BE 기동 후 수동 검증.
```

- [ ] **Step 4: TODO 갱신**

`TODO-READY.md`에서 "FE `FE/`에 Next.js 프로젝트 초기화" 항목을 제거한다(READY가 비면 안내 문구만 남긴다).

`TODO-BACKLOG.md`의 "CORS 설정" 항목을 다음으로 교체(닫기):

```markdown
* [x] ~~CORS 설정~~ — BFF 채택으로 브라우저 경로는 same-origin이라 불필요. 모바일 앱 등 BE 직접 호출 소비처가 생기면 재도입 검토. (2026-07-15 결정)
```

`TODO-BACKLOG.md`의 기능 목록에 추가:

```markdown
* [ ] FE: MEMBER 프로필 화면(조회/수정/이미지) — BE API·BFF 패턴 준비됨
* [ ] FE: 카카오 소셜 로그인 연동(인가코드 리다이렉트 → BFF 교환)
```

`TODO-DONE.md` 맨 위에 추가:

```markdown
* [x] (2026-07-15) FE Next.js 초기화 + 인증 플로우(BFF) (TDD, subagent-driven)
  * Next 16(App Router)/React 19/TS/Tailwind/Vitest 스캐폴딩, 위치 `FE/`
  * BFF 3계층: `lib/server/*`(beClient·cookies·session) / `app/api/bff/**`(signup·login·logout·me) / UI. 토큰은 httpOnly 쿠키, UI 미접근
  * 화면: 회원가입/로그인/대시보드(+루트 리다이렉트), `/dashboard`는 미들웨어 보호
  * reissue 1회 재시도(`session.ts`), 대시보드가 `/api/bff/me`(인증 프로브)로 전체 경로 검증
  * 통신은 네이티브 fetch(라이브러리 미도입). CORS는 BFF라 미추가
  * Vitest 단위 테스트(unwrap·cookies·beClient·session·bff·api·폼 스모크). 실BE 연동은 수동 검증
```

- [ ] **Step 5: 기록 문서 갱신**

`AI-ACTION-LOGS.md` 맨 아래(시간 오름차순 관행)에 추가:

```markdown
* 2026-07-15 — FE Next.js 초기화 + 인증 플로우(BFF) TDD 구현(브랜치 feature/fe-init-auth-bff). Next16/React19/TS/Tailwind/Vitest. BFF 3계층으로 토큰을 UI에서 격리(httpOnly 쿠키), 회원가입/로그인/대시보드 + 미들웨어 보호 + reissue 1회 재시도. 통신은 네이티브 fetch, CORS는 BFF라 미추가. build+vitest 통과, 실BE 연동은 수동 검증.
```

`AI-MAJOR-EVENT.md` 맨 아래에 추가:

```markdown
## 2026-07-15 — FE 스택 및 인증 아키텍처(BFF) 확정

### 주요 의사결정
* **FE 스택**: Next.js 16 App Router + React 19 + TypeScript + Tailwind v4 + Vitest, npm. 위치 `FE/`.
* **BFF 패턴 채택**: 브라우저는 Next(same-origin)하고만 통신, Next 서버가 Spring을 서버 간 호출. 토큰을 httpOnly 쿠키로 다뤄 UI JavaScript가 토큰을 만지지 않게 함(XSS 시 토큰 탈취 차단). 대안(localStorage·access메모리)보다 구현이 크지만 보안 우위로 채택.
* **CORS 미도입**: BFF라 브라우저 경로가 same-origin이고 Next→Spring은 서버 간 호출이라 CORS(브라우저 기제)가 불필요. 당초 BACKLOG의 CORS 항목을 닫고, 모바일 앱 등 직접 호출 소비처가 생기면 재도입하기로 함.
* **통신 방식**: 네이티브 fetch만 사용. axios·React Query는 현 규모에 과함. 통신 지연 대응·대규모 상태 동기화가 필요해지면 React Query 재검토(사용자 확인).
* **미들웨어 보호 범위**: `/dashboard`는 쿠키 '존재'만 검사(서명 검증 안 함) — FE에 JWT 시크릿을 두지 않기 위함. 실제 검증은 BE가 API 호출 때 수행하고 만료는 reissue가 처리.
```

`AI-MAJOR-EVENT-RECAP.md` 맨 아래에 추가:

```markdown
* **2026-07-15 FE 초기화 + 인증(BFF)**: Next.js 16으로 FE 시작. BFF+httpOnly 쿠키로 토큰을 UI에서 격리.
  * 회원가입/로그인/대시보드 + 미들웨어 보호 + reissue 1회 재시도
  * CORS는 BFF라 미도입(브라우저 same-origin), 통신은 네이티브 fetch(라이브러리 미도입)
```

- [ ] **Step 6: 수동 E2E 검증 안내(문서에 남기고, 가능하면 수행)**

아래를 실행해 실제 연동을 확인한다(Docker/BE 기동 가능한 경우). 불가하면 보고서에 "수동 미검증"으로 남긴다.

```bash
# 1) BE 기동
docker compose up -d
# 별도 셸에서:
BE/gradlew -p BE bootRun
# 2) FE 기동 (또 다른 셸에서)
cp FE/.env.local.example FE/.env.local
npm --prefix FE run dev
```

브라우저에서 `http://localhost:3000/signup` → 가입 → `/login` 로그인 → `/dashboard`에 "환영합니다" 표시 → 로그아웃 → `/dashboard` 재접근 시 `/login`으로 리다이렉트되는지 확인. 결과를 `AI-ACTION-LOGS.md`에 한 줄 남긴다.

- [ ] **Step 7: 최종 빌드 + 커밋**

```bash
npm --prefix FE run build
git add docs
git commit -m "docs: FE 초기화·인증 플로우(BFF) 반영 및 아키텍처 결정 기록"
```

---

## 완료 후 남는 것 (플랜 범위 밖)

- **노션 동기화** — 컨트롤러(메인 세션)가 병합 후: TODO 보드 FE 항목 DONE, 스케줄 이벤트.
- **수동 E2E** — Docker/BE 기동이 불가했다면 병합 후 사용자 환경에서 확인.
- MEMBER 프로필 화면, 카카오 소셜 FE 연동, 공통 레이아웃·디자인 다듬기, E2E(Playwright) 도입.
