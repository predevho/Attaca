'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getBff, postBff } from '@/lib/api';

export default function DashboardPage() {
  const router = useRouter();
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    // 인증 프로브: /api/bff/me가 성공하면 세션이 살아있는 것(내부에서 reissue 처리).
    getBff('/api/bff/me').then((res) => {
      if (res.ok) setLoaded(true);
      else router.push('/login');
    });
  }, [router]);

  async function onLogout() {
    await postBff('/api/bff/logout');
    router.push('/login');
  }

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="mb-6 text-2xl font-bold">대시보드</h1>
      <p className="mb-6">{loaded ? '로그인된 상태입니다. 환영합니다!' : '확인 중...'}</p>
      <a href="/profile" className="mr-2 rounded border px-4 py-2">내 프로필</a>
      <button onClick={onLogout} className="rounded border px-4 py-2">로그아웃</button>
    </main>
  );
}
