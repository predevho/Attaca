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
