'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postBff } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPending(true);
    setError(null);
    const res = await postBff('/api/bff/login', { loginId, password });
    setPending(false);
    if (res.ok) router.push('/dashboard');
    else setError(res.message ?? '로그인에 실패했습니다.');
  }

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="mb-6 text-2xl font-bold">로그인</h1>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          아이디
          <input className="rounded border px-3 py-2" value={loginId}
            onChange={(e) => setLoginId(e.target.value)} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          비밀번호
          <input type="password" className="rounded border px-3 py-2" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={pending}
          className="rounded bg-black py-2 text-white disabled:opacity-50">로그인</button>
      </form>
      <p className="mt-4 text-sm">
        계정이 없으신가요? <a href="/signup" className="underline">회원가입</a>
      </p>
    </main>
  );
}
