import 'server-only';

export const ACCESS_COOKIE = 'access_token';
export const REFRESH_COOKIE = 'refresh_token';

const ACCESS_MAX_AGE = 1800;      // 30분(BE access 만료와 일치)
const REFRESH_MAX_AGE = 1209600;  // 14일(BE refresh 만료와 일치)

/** next/headers의 cookies()가 반환하는 저장소의 최소 인터페이스(테스트를 위해 주입 가능하게 둔다). */
export type CookieStore = {
  set(name: string, value: string, options: Record<string, unknown>): void;
  delete(name: string): void;
  get(name: string): { value: string } | undefined;
};

function baseOptions(maxAge: number) {
  return {
    httpOnly: true,
    sameSite: 'lax' as const,
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge,
  };
}

export function setAccessCookie(store: CookieStore, access: string): void {
  store.set(ACCESS_COOKIE, access, baseOptions(ACCESS_MAX_AGE));
}

export function setAuthCookies(store: CookieStore, access: string, refresh: string): void {
  setAccessCookie(store, access);
  store.set(REFRESH_COOKIE, refresh, baseOptions(REFRESH_MAX_AGE));
}

export function clearAuthCookies(store: CookieStore): void {
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
}
