import { NextResponse } from 'next/server';
import { beFetch } from '@/lib/server/beClient';

export async function POST(request: Request) {
  const body = await request.text();
  const res = await beFetch('/api/auth/signup', { method: 'POST', body });
  return NextResponse.json({ ok: res.ok, message: res.message }, { status: res.status || 200 });
}
