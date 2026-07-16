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
