import 'server-only';
import type { CookieStore } from '@/lib/server/cookies';

export const STATE_COOKIE = 'oauth_state';
const STATE_MAX_AGE = 600; // 10분

/** CSRF state를 생성해 httpOnly 쿠키에 저장하고 그 값을 반환한다. */
export function issueState(store: CookieStore): string {
  const state = crypto.randomUUID();
  store.set(STATE_COOKIE, state, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: STATE_MAX_AGE,
  });
  return state;
}

/** 쿠키의 state와 콜백 URL의 state가 정확히 일치하면 true. */
export function verifyState(store: CookieStore, urlState: string | null): boolean {
  const cookie = store.get(STATE_COOKIE)?.value;
  return !!cookie && !!urlState && cookie === urlState;
}

/** state 쿠키를 삭제한다(단일 사용). */
export function clearState(store: CookieStore): void {
  store.delete(STATE_COOKIE);
}
