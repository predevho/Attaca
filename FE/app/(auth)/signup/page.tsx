'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postBff } from '@/lib/api';

export default function SignupPage() {
  const router = useRouter();
  const [form, setForm] = useState({ loginId: '', password: '', email: '', nickname: '' });
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function update(key: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPending(true);
    setError(null);
    const res = await postBff('/api/bff/signup', form);
    setPending(false);
    if (res.ok) router.push('/login');
    else setError(res.message ?? '회원가입에 실패했습니다.');
  }

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="mb-6 text-2xl font-bold">회원가입</h1>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">아이디
          <input className="rounded border px-3 py-2" value={form.loginId} onChange={update('loginId')} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">비밀번호
          <input type="password" className="rounded border px-3 py-2" value={form.password} onChange={update('password')} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">이메일
          <input type="email" className="rounded border px-3 py-2" value={form.email} onChange={update('email')} required />
        </label>
        <label className="flex flex-col gap-1 text-sm">닉네임
          <input className="rounded border px-3 py-2" value={form.nickname} onChange={update('nickname')} required />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={pending}
          className="rounded bg-black py-2 text-white disabled:opacity-50">회원가입</button>
      </form>
      <p className="mt-4 text-sm">
        이미 계정이 있으신가요? <a href="/login" className="underline">로그인</a>
      </p>
    </main>
  );
}
