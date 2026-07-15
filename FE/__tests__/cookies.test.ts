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
