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
