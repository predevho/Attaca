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
