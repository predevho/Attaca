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
