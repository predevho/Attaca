// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
import { STATE_COOKIE, issueState, verifyState, clearState } from '@/lib/server/oauthState';
import type { CookieStore } from '@/lib/server/cookies';

function fakeStore(): CookieStore & { jar: Record<string, string> } {
  const jar: Record<string, string> = {};
  return {
    jar,
    set: vi.fn((n: string, v: string) => { jar[n] = v; }),
    delete: vi.fn((n: string) => { delete jar[n]; }),
    get: (n: string) => (n in jar ? { value: jar[n] } : undefined),
  };
}

describe('oauthState', () => {
  it('issueState는 state를 만들고 httpOnly 쿠키에 저장한다', () => {
    const store = fakeStore();
    const state = issueState(store);

    expect(state).toBeTruthy();
    expect(store.jar[STATE_COOKIE]).toBe(state);
    const opts = (store.set as any).mock.calls[0][2];
    expect(opts).toMatchObject({ httpOnly: true, sameSite: 'lax', path: '/', maxAge: 600 });
  });

  it('verifyState는 쿠키와 URL state가 일치할 때만 true', () => {
    const store = fakeStore();
    const state = issueState(store);

    expect(verifyState(store, state)).toBe(true);
    expect(verifyState(store, 'other')).toBe(false);
    expect(verifyState(store, null)).toBe(false);
  });

  it('쿠키가 없으면 verifyState는 false', () => {
    const store = fakeStore();
    expect(verifyState(store, 'anything')).toBe(false);
  });

  it('clearState는 state 쿠키를 삭제한다', () => {
    const store = fakeStore();
    issueState(store);
    clearState(store);
    expect(store.jar[STATE_COOKIE]).toBeUndefined();
  });
});
