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
