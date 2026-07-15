import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { beFetch } from '@/lib/server/beClient';
import { setAuthCookies } from '@/lib/server/cookies';

export async function POST(request: Request) {
  const body = await request.text();
  const res = await beFetch('/api/auth/login', { method: 'POST', body });

  if (res.ok) {
    const { accessToken, refreshToken } = res.data as { accessToken: string; refreshToken: string };
    setAuthCookies(await cookies(), accessToken, refreshToken);
  }
  return NextResponse.json({ ok: res.ok, message: res.message }, { status: res.status || 200 });
}
