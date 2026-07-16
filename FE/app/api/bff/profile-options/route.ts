import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authedBeFetch } from '@/lib/server/session';

export async function GET() {
  const res = await authedBeFetch(await cookies(), '/api/members/profile-options');
  return NextResponse.json({ ok: res.ok, data: res.data, message: res.message }, { status: res.status || 200 });
}
