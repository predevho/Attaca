import { NextResponse, type NextRequest } from 'next/server';

// 쿠키 이름을 여기서 하드코딩하는 이유: lib/server/cookies.ts는 'server-only'라 edge 런타임인
// 미들웨어에서 import할 수 없다. 이름이 바뀌면 두 곳을 함께 고칠 것(access_token).
const ACCESS_COOKIE = 'access_token';

/**
 * /dashboard, /profile 보호. access 쿠키의 '존재'만 검사한다(서명 검증은 BE가 실제 호출 시 수행).
 * 만료된 access여도 통과시키고, 데이터 호출 단계에서 reissue가 처리한다 —
 * FE에 JWT 시크릿을 두지 않기 위함.
 */
export function middleware(req: NextRequest) {
  const hasAccess = req.cookies.has(ACCESS_COOKIE);
  if (!hasAccess) {
    const loginUrl = new URL('/login', req.url);
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = { matcher: ['/dashboard/:path*', '/profile/:path*'] };
