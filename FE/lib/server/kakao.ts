import 'server-only';
import { beFetch, type BeResult } from '@/lib/server/beClient';

const KAKAO_AUTHORIZE_URL = 'https://kauth.kakao.com/oauth/authorize';

/** 카카오 authorize URL을 구성한다. client_id/redirect_uri는 서버 env. */
export function buildAuthorizeUrl(state: string): string {
  const params = new URLSearchParams({
    client_id: process.env.KAKAO_CLIENT_ID ?? '',
    redirect_uri: process.env.KAKAO_REDIRECT_URI ?? '',
    response_type: 'code',
    state,
  });
  return `${KAKAO_AUTHORIZE_URL}?${params.toString()}`;
}

/** BE에 인가코드를 넘겨 토큰으로 교환한다. redirectUri는 authorize와 동일해야 한다. */
export function exchangeCode(code: string, redirectUri: string): Promise<BeResult> {
  return beFetch('/api/auth/oauth/kakao', {
    method: 'POST',
    body: JSON.stringify({ code, redirectUri }),
  });
}
