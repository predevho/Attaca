import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { verifyState, clearState } from '@/lib/server/oauthState';
import { exchangeCode } from '@/lib/server/kakao';
import { setAuthCookies } from '@/lib/server/cookies';

export async function GET(request: Request) {
  const url = new URL(request.url);
  const origin = url.origin;
  const store = await cookies();
  const redirect = (path: string) => NextResponse.redirect(new URL(path, origin));

  const kakaoError = url.searchParams.get('error');
  const code = url.searchParams.get('code');
  const urlState = url.searchParams.get('state');

  const stateOk = verifyState(store, urlState);
  clearState(store); // 단일 사용: 어느 경로로 끝나든 삭제

  if (kakaoError) return redirect('/login?error=kakao_cancelled');
  if (!stateOk) return redirect('/login?error=state');
  if (!code) return redirect('/login?error=oauth');

  const res = await exchangeCode(code, process.env.KAKAO_REDIRECT_URI ?? '');
  if (!res.ok) return redirect('/login?error=oauth');

  const { accessToken, refreshToken } = res.data as { accessToken: string; refreshToken: string };
  setAuthCookies(store, accessToken, refreshToken);
  return redirect('/dashboard');
}
