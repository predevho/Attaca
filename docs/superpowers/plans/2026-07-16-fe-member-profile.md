# FE MEMBER 프로필 화면(조회/수정/이미지) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/profile`에 조회/수정 프로필 화면을 만들고, BE 프로필 API 3종(수정·이미지·선택지)을 BFF로 연동한다. 이미지는 멀티파트로 즉시 업로드한다.

**Architecture:** 기존 FE BFF 인증(`authedBeFetch` = Bearer + reissue) 위에 프로필 BFF 라우트 3개를 추가한다. 멀티파트를 위해 `beFetch`가 FormData body일 때 JSON content-type을 붙이지 않도록 고친다. `/profile`은 클라이언트 컴포넌트로 조회/편집 모드를 오간다.

**Tech Stack:** Next.js 16(App Router) / React 19 / TypeScript / Vitest 4 / 네이티브 fetch.

**설계 문서:** `docs/superpowers/specs/2026-07-16-fe-member-profile-design.md`

## Global Constraints

- 위치 `FE/`. 통신은 네이티브 fetch만. 토큰은 UI에서 접근하지 않는다(BFF만 호출).
- `lib/server/*`는 `import 'server-only'`. 쿠키 R/W·BE 호출은 서버 라우트/서버 코드에서만.
- 모든 프로필 BE 호출은 `authedBeFetch`(Bearer + 401 시 reissue 1회) 경유. UI엔 `{ok, data?, message}`만.
- BE 계약: `PUT /api/members/me/profile`(JSON `{instruments[], bio}`) / `PUT /api/members/me/profile/image`(multipart part `file`) / `GET /api/members/profile-options`(`{instruments:[{code,label}]}`). 조회는 기존 `GET /api/members/me/profile`(`{instruments[], bio, profileImageUrl}`).
- 순수 서버 로직/라우트 테스트는 `// @vitest-environment node`(첫 줄). 컴포넌트 테스트는 jsdom(기본).
- 각 태스크 끝에서 `npm --prefix FE run build`와 `npm --prefix FE test` 통과.
- 커밋 메시지 한글. 실제 이미지 S3 검증은 범위 밖(storage.type=local).

---

## 기존 재사용

- `lib/server/beClient.ts`: `beFetch(path, init?) → BeResult{ok,status,data,message}`.
- `lib/server/session.ts`: `authedBeFetch(store, path, init?)` — FormData body도 재시도에 재사용 가능(스트림 아님).
- `lib/api.ts`: `getBff`/`postBff`. `lib/server/cookies.ts`: `CookieStore`.
- Vitest 하네스: `server-only` 스텁 처리됨. 하네스 건드리지 말 것.

---

## File Structure

| 파일 | 작업 |
|---|---|
| `FE/lib/server/beClient.ts` | 수정 — FormData면 content-type 생략 |
| `FE/lib/api.ts` | 추가 — `putBff`, `putBffForm` |
| `FE/app/api/bff/me/profile/route.ts` | 생성 — PUT 프로필 수정 |
| `FE/app/api/bff/me/profile/image/route.ts` | 생성 — PUT 이미지(멀티파트) |
| `FE/app/api/bff/profile-options/route.ts` | 생성 — GET 선택지 |
| `FE/app/profile/page.tsx` | 생성 — 프로필 화면(클라이언트) |
| `FE/middleware.ts` | 수정 — matcher에 `/profile` |
| `FE/app/dashboard/page.tsx` | 수정 — 내 프로필 링크 |

---

## Task 1: beFetch 멀티파트 지원 + 클라이언트 PUT 헬퍼

**Files:**
- Modify: `FE/lib/server/beClient.ts`
- Modify: `FE/lib/api.ts`
- Test: `FE/__tests__/beClient-multipart.test.ts`, `FE/__tests__/api-put.test.ts`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `beFetch`가 body가 `FormData`이면 기본 `content-type`을 붙이지 않음(JSON 경로 불변)
  - `putBff<T>(path, body?) → Promise<BffResult<T>>`, `putBffForm<T>(path, form: FormData) → Promise<BffResult<T>>`

- [ ] **Step 1: 실패 테스트 작성**

`FE/__tests__/beClient-multipart.test.ts`:

```ts
// @vitest-environment node
import { describe, it, expect, vi, afterEach } from 'vitest';
import { beFetch } from '@/lib/server/beClient';

afterEach(() => { vi.unstubAllGlobals(); });

function ok() {
  return new Response(JSON.stringify({ success: true, data: null, error: null }),
    { status: 200, headers: { 'content-type': 'application/json' } });
}

describe('beFetch content-type', () => {
  it('JSON body면 application/json을 붙인다', async () => {
    const fetchMock = vi.fn(async () => ok());
    vi.stubGlobal('fetch', fetchMock);

    await beFetch('/x', { method: 'POST', body: JSON.stringify({ a: 1 }) });

    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers['content-type']).toBe('application/json');
  });

  it('FormData body면 content-type을 붙이지 않는다(boundary 자동)', async () => {
    const fetchMock = vi.fn(async () => ok());
    vi.stubGlobal('fetch', fetchMock);
    const fd = new FormData();
    fd.append('file', new Blob(['x'], { type: 'image/png' }), 'a.png');

    await beFetch('/x', { method: 'PUT', body: fd });

    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers['content-type']).toBeUndefined();
  });
});
```

`FE/__tests__/api-put.test.ts`:

```ts
import { describe, it, expect, vi, afterEach } from 'vitest';
import { putBff, putBffForm } from '@/lib/api';

afterEach(() => { vi.unstubAllGlobals(); });

function json(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
}

describe('client put helpers', () => {
  it('putBff는 상대경로를 JSON PUT한다', async () => {
    const fetchMock = vi.fn(async () => json({ ok: true, message: null }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await putBff('/api/bff/me/profile', { instruments: ['VIOLIN'], bio: 'hi' });

    expect(res.ok).toBe(true);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/bff/me/profile');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('PUT');
    expect((init.headers as Record<string, string>)['content-type']).toBe('application/json');
    expect(JSON.parse(init.body as string)).toEqual({ instruments: ['VIOLIN'], bio: 'hi' });
  });

  it('putBffForm은 FormData를 content-type 없이 PUT한다', async () => {
    const fetchMock = vi.fn(async () => json({ ok: true, data: { profileImageUrl: 'u' }, message: null }));
    vi.stubGlobal('fetch', fetchMock);
    const fd = new FormData();
    fd.append('file', new Blob(['x'], { type: 'image/png' }), 'a.png');

    const res = await putBffForm('/api/bff/me/profile/image', fd);

    expect(res.ok).toBe(true);
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('PUT');
    expect(init.body).toBe(fd);
    expect(init.headers).toBeUndefined();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `putBff`/`putBffForm` 없음, FormData content-type 테스트 실패.

- [ ] **Step 3: 구현**

`FE/lib/server/beClient.ts`의 `beFetch`에서 headers 구성을 다음으로 교체한다. (기존 try/catch·정규화 로직은 그대로)

```ts
export async function beFetch(path: string, init?: RequestInit): Promise<BeResult> {
  const isForm = init?.body instanceof FormData;
  const baseHeaders = isForm ? {} : { 'content-type': 'application/json' };
  let res: Response;
  try {
    res = await fetch(BE_BASE_URL + path, {
      ...init,
      headers: { ...baseHeaders, ...(init?.headers ?? {}) },
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

`FE/lib/api.ts`에 두 헬퍼를 추가한다(기존 `getBff`/`postBff`/`parse` 아래):

```ts
export async function putBff<T = unknown>(path: string, body?: unknown): Promise<BffResult<T>> {
  const res = await fetch(path, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parse<T>(res);
}

/** multipart PUT. content-type을 지정하지 않아 브라우저가 boundary를 설정한다. */
export async function putBffForm<T = unknown>(path: string, form: FormData): Promise<BffResult<T>> {
  const res = await fetch(path, { method: 'PUT', body: form });
  return parse<T>(res);
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
npm --prefix FE test
```

Expected: PASS (신규 4개 포함).

- [ ] **Step 5: 커밋**

```bash
git add FE/lib/server/beClient.ts FE/lib/api.ts FE/__tests__/beClient-multipart.test.ts FE/__tests__/api-put.test.ts
git commit -m "feat: beFetch 멀티파트 지원 및 클라이언트 putBff/putBffForm 헬퍼 추가"
```

---

## Task 2: 프로필 BFF 라우트 3개

**Files:**
- Create: `FE/app/api/bff/me/profile/route.ts`, `FE/app/api/bff/me/profile/image/route.ts`, `FE/app/api/bff/profile-options/route.ts`
- Test: `FE/__tests__/bff-profile.test.ts`

**Interfaces:**
- Consumes: `authedBeFetch`(session), `next/headers` `cookies()`, `next/server` `NextResponse`
- Produces: `PUT /api/bff/me/profile`, `PUT /api/bff/me/profile/image`, `GET /api/bff/profile-options`

- [ ] **Step 1: 실패 테스트 작성**

`FE/__tests__/bff-profile.test.ts`:

```ts
// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const jar: Record<string, string> = { access_token: 'A' };
const cookieStore = {
  set: vi.fn((n: string, v: string) => { jar[n] = v; }),
  delete: vi.fn((n: string) => { delete jar[n]; }),
  get: (n: string) => (n in jar ? { value: jar[n] } : undefined),
};
vi.mock('next/headers', () => ({ cookies: async () => cookieStore }));

beforeEach(() => { vi.clearAllMocks(); });
afterEach(() => { vi.unstubAllGlobals(); });

function beJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('BFF 프로필 라우트', () => {
  it('PUT /api/bff/me/profile → BE로 JSON 통과', async () => {
    const fetchMock = vi.fn(async () => beJson(
      { success: true, data: { instruments: ['VIOLIN'], bio: 'hi', profileImageUrl: null }, error: null }));
    vi.stubGlobal('fetch', fetchMock);
    const { PUT } = await import('@/app/api/bff/me/profile/route');

    const req = new Request('http://localhost/api/bff/me/profile', {
      method: 'PUT', body: JSON.stringify({ instruments: ['VIOLIN'], bio: 'hi' }),
    });
    const res = await PUT(req);
    const body = await res.json();

    expect(res.status).toBe(200);
    expect(body.ok).toBe(true);
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/members/me/profile');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer A');
  });

  it('GET /api/bff/profile-options → 선택지 반환', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => beJson(
      { success: true, data: { instruments: [{ code: 'VIOLIN', label: '바이올린' }] }, error: null })));
    const { GET } = await import('@/app/api/bff/profile-options/route');

    const res = await GET();
    const body = await res.json();

    expect(body.ok).toBe(true);
    expect(body.data.instruments[0]).toEqual({ code: 'VIOLIN', label: '바이올린' });
  });

  it('PUT /api/bff/me/profile/image → file 파트를 BE로 멀티파트 전달', async () => {
    const fetchMock = vi.fn(async () => beJson(
      { success: true, data: { profileImageUrl: 'http://x/img.png' }, error: null }));
    vi.stubGlobal('fetch', fetchMock);
    const { PUT } = await import('@/app/api/bff/me/profile/image/route');

    const fd = new FormData();
    fd.append('file', new Blob(['img'], { type: 'image/png' }), 'a.png');
    const req = new Request('http://localhost/api/bff/me/profile/image', { method: 'PUT', body: fd });
    const res = await PUT(req);
    const body = await res.json();

    expect(res.status).toBe(200);
    expect(body.data.profileImageUrl).toBe('http://x/img.png');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).toBeInstanceOf(Blob);
  });

  it('PUT /api/bff/me/profile/image → file 파트 없으면 400', async () => {
    const { PUT } = await import('@/app/api/bff/me/profile/image/route');
    const req = new Request('http://localhost/api/bff/me/profile/image', { method: 'PUT', body: new FormData() });
    const res = await PUT(req);
    const body = await res.json();

    expect(res.status).toBe(400);
    expect(body.ok).toBe(false);
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — 라우트 모듈 없음.

- [ ] **Step 3: 구현**

`FE/app/api/bff/me/profile/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authedBeFetch } from '@/lib/server/session';

export async function PUT(request: Request) {
  const body = await request.text();
  const res = await authedBeFetch(await cookies(), '/api/members/me/profile', { method: 'PUT', body });
  return NextResponse.json({ ok: res.ok, data: res.data, message: res.message }, { status: res.status || 200 });
}
```

`FE/app/api/bff/profile-options/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authedBeFetch } from '@/lib/server/session';

export async function GET() {
  const res = await authedBeFetch(await cookies(), '/api/members/profile-options');
  return NextResponse.json({ ok: res.ok, data: res.data, message: res.message }, { status: res.status || 200 });
}
```

`FE/app/api/bff/me/profile/image/route.ts`:

```ts
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authedBeFetch } from '@/lib/server/session';

export async function PUT(request: Request) {
  const incoming = await request.formData();
  const file = incoming.get('file');
  if (!(file instanceof Blob)) {
    return NextResponse.json({ ok: false, message: '이미지 파일이 필요합니다.' }, { status: 400 });
  }

  const forward = new FormData();
  forward.append('file', file, (file as File).name ?? 'upload');

  const res = await authedBeFetch(await cookies(), '/api/members/me/profile/image', {
    method: 'PUT',
    body: forward,
  });
  return NextResponse.json({ ok: res.ok, data: res.data, message: res.message }, { status: res.status || 200 });
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
git add FE/app/api/bff/me/profile FE/app/api/bff/profile-options FE/__tests__/bff-profile.test.ts
git commit -m "feat: 프로필 BFF 라우트 추가 - 수정/이미지(멀티파트)/선택지"
```

---

## Task 3: /profile 화면 + 미들웨어 보호 + 대시보드 링크

**Files:**
- Create: `FE/app/profile/page.tsx`
- Modify: `FE/middleware.ts`, `FE/app/dashboard/page.tsx`
- Test: `FE/__tests__/profile-page.test.tsx`

**Interfaces:**
- Consumes: `getBff`/`putBff`/`putBffForm`(api.ts), `useRouter`(next/navigation)
- Produces: `/profile` 라우트(조회/편집), `/profile` 미들웨어 보호

- [ ] **Step 1: 실패 테스트 작성**

`FE/__tests__/profile-page.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

const getBff = vi.fn();
const putBff = vi.fn();
const putBffForm = vi.fn();
vi.mock('@/lib/api', () => ({ getBff: (...a: unknown[]) => getBff(...a), putBff: (...a: unknown[]) => putBff(...a), putBffForm: (...a: unknown[]) => putBffForm(...a) }));

import ProfilePage from '@/app/profile/page';

beforeEach(() => {
  vi.clearAllMocks();
  getBff.mockImplementation(async (path: string) => {
    if (path === '/api/bff/me') return { ok: true, data: { instruments: ['VIOLIN'], bio: '첼로 좋아요', profileImageUrl: null }, message: null };
    if (path === '/api/bff/profile-options') return { ok: true, data: { instruments: [{ code: 'VIOLIN', label: '바이올린' }, { code: 'CELLO', label: '첼로' }] }, message: null };
    return { ok: false, message: 'x' };
  });
});

describe('ProfilePage', () => {
  it('조회 모드에서 악기 label과 자기소개를 보여준다', async () => {
    render(<ProfilePage />);
    expect(await screen.findByText('바이올린')).toBeInTheDocument();
    expect(screen.getByText('첼로 좋아요')).toBeInTheDocument();
  });

  it('수정 버튼을 누르면 편집 모드로 전환된다', async () => {
    render(<ProfilePage />);
    const editBtn = await screen.findByRole('button', { name: '수정' });
    fireEvent.click(editBtn);
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument());
    // 선택지 칩이 렌더된다
    expect(screen.getByRole('button', { name: '첼로' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm --prefix FE test
```

Expected: FAIL — `/app/profile/page` 없음.

- [ ] **Step 3: 구현**

`FE/app/profile/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getBff, putBff, putBffForm } from '@/lib/api';

type Option = { code: string; label: string };
type Profile = { instruments: string[]; bio: string | null; profileImageUrl: string | null };

const MAX_INSTRUMENTS = 10;

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [options, setOptions] = useState<Option[]>([]);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  const [draftInstruments, setDraftInstruments] = useState<string[]>([]);
  const [draftBio, setDraftBio] = useState('');

  useEffect(() => {
    Promise.all([getBff('/api/bff/me'), getBff('/api/bff/profile-options')]).then(([me, opt]) => {
      if (!me.ok) { router.push('/login'); return; }
      setProfile(me.data as Profile);
      if (opt.ok) setOptions((opt.data as { instruments: Option[] }).instruments);
    });
  }, [router]);

  function labelOf(code: string) {
    return options.find((o) => o.code === code)?.label ?? code;
  }

  function startEdit() {
    if (!profile) return;
    setDraftInstruments(profile.instruments);
    setDraftBio(profile.bio ?? '');
    setError(null);
    setEditing(true);
  }

  function toggleInstrument(code: string) {
    setDraftInstruments((cur) => {
      if (cur.includes(code)) return cur.filter((c) => c !== code);
      if (cur.length >= MAX_INSTRUMENTS) { setError(`악기는 최대 ${MAX_INSTRUMENTS}개까지 선택할 수 있습니다.`); return cur; }
      return [...cur, code];
    });
  }

  async function save() {
    setError(null);
    const res = await putBff<Profile>('/api/bff/me/profile', { instruments: draftInstruments, bio: draftBio });
    if (res.ok) { setProfile(res.data as Profile); setEditing(false); }
    else setError(res.message ?? '저장에 실패했습니다.');
  }

  async function onImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) { setError('이미지 파일만 업로드할 수 있습니다.'); return; }
    setError(null);
    setUploading(true);
    const fd = new FormData();
    fd.append('file', file);
    const res = await putBffForm<{ profileImageUrl: string }>('/api/bff/me/profile/image', fd);
    setUploading(false);
    if (res.ok && profile) setProfile({ ...profile, profileImageUrl: (res.data as { profileImageUrl: string }).profileImageUrl });
    else setError(res.message ?? '이미지 업로드에 실패했습니다.');
  }

  if (!profile) return <main className="mx-auto mt-24 max-w-md px-4">불러오는 중...</main>;

  return (
    <main className="mx-auto mt-16 max-w-md px-4">
      <h1 className="mb-6 text-2xl font-bold">내 프로필</h1>

      <div className="mb-6 flex items-center gap-4">
        {profile.profileImageUrl
          ? <img src={profile.profileImageUrl} alt="프로필" className="h-20 w-20 rounded-full object-cover" />
          : <div className="flex h-20 w-20 items-center justify-center rounded-full bg-gray-200 text-xs text-gray-500">사진 없음</div>}
        <label className="cursor-pointer rounded border px-3 py-1.5 text-sm">
          {uploading ? '업로드 중...' : '이미지 변경'}
          <input type="file" accept="image/*" className="hidden" onChange={onImageChange} disabled={uploading} />
        </label>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {!editing ? (
        <section className="flex flex-col gap-4">
          <div>
            <h2 className="mb-2 text-sm font-medium text-gray-500">악기</h2>
            {profile.instruments.length > 0
              ? <div className="flex flex-wrap gap-2">{profile.instruments.map((c) => (
                  <span key={c} className="rounded-full bg-indigo-100 px-3 py-1 text-sm text-indigo-800">{labelOf(c)}</span>))}</div>
              : <p className="text-sm text-gray-400">등록된 악기가 없습니다.</p>}
          </div>
          <div>
            <h2 className="mb-2 text-sm font-medium text-gray-500">자기소개</h2>
            <p className="whitespace-pre-wrap text-sm">{profile.bio || <span className="text-gray-400">자기소개가 없습니다.</span>}</p>
          </div>
          <div className="mt-2 flex gap-2">
            <button onClick={startEdit} className="rounded bg-black px-4 py-2 text-white">수정</button>
            <a href="/dashboard" className="rounded border px-4 py-2 text-center">대시보드</a>
          </div>
        </section>
      ) : (
        <section className="flex flex-col gap-4">
          <div>
            <h2 className="mb-2 text-sm font-medium text-gray-500">악기 (최대 {MAX_INSTRUMENTS}개)</h2>
            <div className="flex flex-wrap gap-2">
              {options.map((o) => {
                const on = draftInstruments.includes(o.code);
                return (
                  <button key={o.code} type="button" onClick={() => toggleInstrument(o.code)}
                    className={`rounded-full px-3 py-1 text-sm ${on ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-700'}`}>
                    {o.label}
                  </button>
                );
              })}
            </div>
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-gray-500">자기소개 ({draftBio.length}/500)</label>
            <textarea value={draftBio} maxLength={500} onChange={(e) => setDraftBio(e.target.value)}
              className="h-32 w-full rounded border px-3 py-2 text-sm" />
          </div>
          <div className="mt-2 flex gap-2">
            <button onClick={save} className="rounded bg-black px-4 py-2 text-white">저장</button>
            <button onClick={() => { setEditing(false); setError(null); }} className="rounded border px-4 py-2">취소</button>
          </div>
        </section>
      )}
    </main>
  );
}
```

`FE/middleware.ts`의 matcher를 교체:

```ts
export const config = { matcher: ['/dashboard/:path*', '/profile/:path*'] };
```

`FE/app/dashboard/page.tsx`의 로그아웃 버튼 앞에 프로필 링크를 추가한다. 기존 `<button onClick={onLogout} ...>` 위에:

```tsx
      <a href="/profile" className="mr-2 rounded border px-4 py-2">내 프로필</a>
```

- [ ] **Step 4: 테스트 통과 + 빌드 확인**

```bash
npm --prefix FE test
npm --prefix FE run build
```

Expected: 테스트 PASS / 빌드 성공.

- [ ] **Step 5: 커밋**

```bash
git add FE/app/profile FE/middleware.ts FE/app/dashboard/page.tsx FE/__tests__/profile-page.test.tsx
git commit -m "feat: 내 프로필 화면(조회/수정/이미지) 추가 및 /profile 보호"
```

---

## Task 4: 문서 반영 + 수동 검증 안내

**Files:**
- Modify: `docs/ARCHITECTURE-STATUTE.md`, `docs/CONTEXT.md`, `docs/DOMAIN-MEMBER-STATUTE.md`, `docs/TODO-BACKLOG.md`, `docs/AI-ACTION-LOGS.md`, `docs/AI-MAJOR-EVENT.md`, `docs/AI-MAJOR-EVENT-RECAP.md`

- [ ] **Step 1: `ARCHITECTURE-STATUTE.md` FE 절 보강**

BFF 하위 설명에 한 줄 추가:

```markdown
  * 프로필: `/profile`(조회/수정 모드) + BFF `PUT /api/bff/me/profile`(악기·자기소개), `PUT /api/bff/me/profile/image`(멀티파트 즉시 업로드), `GET /api/bff/profile-options`. 멀티파트 위해 `beFetch`는 body가 FormData면 content-type을 붙이지 않는다.
```

- [ ] **Step 2: `CONTEXT.md` 주의 추가**

FE 카카오 항목 뒤에 추가:

```markdown
* FE 프로필: `/profile`(인증 필요, 미들웨어 보호). 조회 기본 + 수정 모드(악기 칩 최대10·자기소개 500자, `PUT /api/bff/me/profile`). 이미지는 파일 선택 즉시 업로드(`PUT /api/bff/me/profile/image`, 멀티파트). 악기 코드↔label은 `/api/bff/profile-options`로 변환. `beFetch`는 FormData면 content-type 미설정(멀티파트).
```

- [ ] **Step 3: `DOMAIN-MEMBER-STATUTE.md` §5 갱신**

`* 프로필 이미지 업로드 (FileStorage 경유)` 및 `* 내 프로필 조회/수정` 항목에 FE 완료 주석을 붙인다:

```markdown
* 내 프로필 조회/수정 (BE 2026-07-15, FE 화면 2026-07-16 완료)
* 프로필 이미지 업로드 (FileStorage 경유; FE 즉시 업로드 2026-07-16 완료)
```

- [ ] **Step 4: `TODO-BACKLOG.md` 항목 닫기**

`* [ ] FE: MEMBER 프로필 화면(조회/수정/이미지) — BE API·BFF 패턴 준비됨`을 다음으로 교체:

```markdown
* [x] ~~FE: MEMBER 프로필 화면(조회/수정/이미지)~~ — 2026-07-16 완료(조회/수정 모드, 이미지 즉시 업로드, beFetch 멀티파트 지원). 실이미지 S3 검증만 별개 BACKLOG.
```

- [ ] **Step 5: 기록 문서 갱신**

`AI-ACTION-LOGS.md` 맨 아래에 추가:

```markdown
* 2026-07-16 — FE MEMBER 프로필 화면 TDD 구현(브랜치 feature/fe-member-profile). `/profile` 조회/수정 모드(악기 칩 토글·자기소개 500자) + 이미지 파일 선택 즉시 업로드. BFF 라우트 3종(프로필 PUT·이미지 PUT 멀티파트·선택지 GET) authedBeFetch 경유. `beFetch`를 FormData면 content-type 미설정으로 수정(멀티파트 지원, JSON 경로 불변). `putBff`/`putBffForm` 헬퍼. `/profile` 미들웨어 보호, 대시보드 링크. build+vitest 통과.
```

`AI-MAJOR-EVENT.md` 맨 아래에 추가:

```markdown
## 2026-07-16 — FE MEMBER 프로필 화면 및 멀티파트 BFF 확정

### 주요 의사결정
* **조회/수정 모드 전환 UI**: `/profile` 기본 조회, "수정" 버튼으로 편집 폼(악기·자기소개). 이미지는 BE 엔드포인트가 분리돼 있어 파일 선택 즉시 업로드(악기·소개 저장과 독립).
* **PUT(전체 교체) 유지**: 편집 폼이 프로필 전체를 들고 있으므로 부분수정(PATCH)이 아니라 전체 교체 PUT이 자연스럽고 멱등. BE 계약과도 일치. 프로필이 커지고 인라인 부분수정 UX가 생기면 PATCH 재검토.
* **멀티파트 지원 방식**: 공유 `beFetch`가 body=FormData면 JSON content-type을 붙이지 않도록 수정(boundary 자동). authedBeFetch(Bearer+reissue)를 멀티파트에도 그대로 재사용, 이미지 BFF는 파일 파트를 새 FormData로 재구성해 BE에 전달.
```

`AI-MAJOR-EVENT-RECAP.md` 맨 아래에 추가:

```markdown
* **2026-07-16 FE 프로필 화면**: `/profile` 조회/수정 모드 + 이미지 즉시 업로드.
  * 악기 칩 토글·자기소개, PUT 전체 교체 유지(폼이 전체를 쥠). BFF 라우트 3종 authedBeFetch 경유
  * beFetch를 FormData면 content-type 미설정으로 고쳐 멀티파트 지원(JSON 경로 불변)
```

- [ ] **Step 6: 수동 검증 안내(가능 시) 후 최종 빌드·커밋**

BE+FE를 띄울 수 있으면 확인한다(불가하면 보고서에 "미수행"):
- 로그인 → `/profile` → 수정 모드에서 악기 선택·자기소개 저장 → 조회 반영
- 이미지 변경 → 즉시 반영(로컬 `/files/**` 서빙)

```bash
npm --prefix FE run build
git add docs
git commit -m "docs: FE 프로필 화면 및 멀티파트 BFF 반영"
```

---

## 완료 후 남는 것 (플랜 범위 밖)

- **노션 동기화** — 컨트롤러가 병합 후 처리.
- **수동 검증** — BE 기동 후 프로필 수정·이미지 업로드 확인(카카오 때처럼 컨트롤러가 브라우저로 수행 가능).
- 다른 회원 프로필 조회, 이미지 크롭, VERIFIED-PERFORMER 뱃지 노출.
