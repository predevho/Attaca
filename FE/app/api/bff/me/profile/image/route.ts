import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authedBeFetch } from '@/lib/server/session';

export async function PUT(request: Request) {
  const incoming = await request.formData();
  const file = incoming.get('file');
  if (!(file instanceof Blob)) {
    return NextResponse.json({ ok: false, message: '이미지 파일이 필요합니다.' }, { status: 400 });
  }

  const forward = new FormData();
  forward.append('file', file, (file as File).name ?? 'upload');

  const res = await authedBeFetch(await cookies(), '/api/members/me/profile/image', {
    method: 'PUT',
    body: forward,
  });
  return NextResponse.json({ ok: res.ok, data: res.data, message: res.message }, { status: res.status || 200 });
}
