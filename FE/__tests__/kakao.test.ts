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
