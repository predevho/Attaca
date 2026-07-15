import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { issueState } from '@/lib/server/oauthState';
import { buildAuthorizeUrl } from '@/lib/server/kakao';

export async function GET(request: Request) {
  const origin = new URL(request.url).origin;
  if (!process.env.KAKAO_CLIENT_ID) {
    return NextResponse.redirect(new URL('/login?error=oauth_config', origin));
  }
  const state = issueState(await cookies());
  return NextResponse.redirect(buildAuthorizeUrl(state));
}
