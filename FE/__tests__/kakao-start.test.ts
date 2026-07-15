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
afterEach(() => { process.env = { ...OLD_ENV }; });

function locationOf(res: Response) {
  const u = new URL(res.headers.get('location')!);
  return u;
}

describe('GET /api/bff/oauth/kakao/start', () => {
  it('KAKAO_CLIENT_ID 없으면 state 없이 /login?error=oauth_config로 리다이렉트', async () => {
    delete process.env.KAKAO_CLIENT_ID;
    const { GET } = await import('@/app/api/bff/oauth/kakao/start/route');

    const res = await GET(new Request('http://localhost:3000/api/bff/oauth/kakao/start'));

    const u = locationOf(res);
    expect(u.pathname + u.search).toBe('/login?error=oauth_config');
    expect(jar['oauth_state']).toBeUndefined();
  });

  it('KAKAO_CLIENT_ID 있으면 state 쿠키 설정 후 카카오 authorize URL로 리다이렉트', async () => {
    process.env.KAKAO_CLIENT_ID = 'rest-key';
    const { GET } = await import('@/app/api/bff/oauth/kakao/start/route');

    const res = await GET(new Request('http://localhost:3000/api/bff/oauth/kakao/start'));

    const u = locationOf(res);
    expect(u.origin + u.pathname).toBe('https://kauth.kakao.com/oauth/authorize');
    expect(u.searchParams.get('client_id')).toBe('rest-key');
    expect(u.searchParams.get('redirect_uri')).toBe('http://localhost:3000/api/bff/oauth/kakao/callback');
    expect(u.searchParams.get('response_type')).toBe('code');

    // authorize URL의 state가 쿠키에 저장된 값과 일치해야 한다(콜백 대조의 근거)
    const savedState = jar['oauth_state'];
    expect(savedState).toBeTruthy();
    expect(u.searchParams.get('state')).toBe(savedState);
  });
});
