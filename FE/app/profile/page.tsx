'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getBff, putBff, putBffForm } from '@/lib/api';

type Option = { code: string; label: string };
type Profile = { instruments: string[]; bio: string | null; profileImageUrl: string | null };

const MAX_INSTRUMENTS = 10;

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [options, setOptions] = useState<Option[]>([]);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  const [draftInstruments, setDraftInstruments] = useState<string[]>([]);
  const [draftBio, setDraftBio] = useState('');

  useEffect(() => {
    Promise.all([getBff('/api/bff/me'), getBff('/api/bff/profile-options')]).then(([me, opt]) => {
      if (!me.ok) { router.push('/login'); return; }
      setProfile(me.data as Profile);
      if (opt.ok) setOptions((opt.data as { instruments: Option[] }).instruments);
    });
  }, [router]);

  function labelOf(code: string) {
    return options.find((o) => o.code === code)?.label ?? code;
  }

  function startEdit() {
    if (!profile) return;
    setDraftInstruments(profile.instruments);
    setDraftBio(profile.bio ?? '');
    setError(null);
    setEditing(true);
  }

  function toggleInstrument(code: string) {
    setDraftInstruments((cur) => {
      if (cur.includes(code)) { setError(null); return cur.filter((c) => c !== code); }
      if (cur.length >= MAX_INSTRUMENTS) { setError(`악기는 최대 ${MAX_INSTRUMENTS}개까지 선택할 수 있습니다.`); return cur; }
      setError(null);
      return [...cur, code];
    });
  }

  async function save() {
    setError(null);
    const res = await putBff<Profile>('/api/bff/me/profile', { instruments: draftInstruments, bio: draftBio });
    if (res.ok) { setProfile(res.data as Profile); setEditing(false); }
    else setError(res.message ?? '저장에 실패했습니다.');
  }

  async function onImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) { setError('이미지 파일만 업로드할 수 있습니다.'); return; }
    setError(null);
    setUploading(true);
    const fd = new FormData();
    fd.append('file', file);
    const res = await putBffForm<{ profileImageUrl: string }>('/api/bff/me/profile/image', fd);
    setUploading(false);
    if (res.ok && profile) setProfile({ ...profile, profileImageUrl: (res.data as { profileImageUrl: string }).profileImageUrl });
    else setError(res.message ?? '이미지 업로드에 실패했습니다.');
  }

  if (!profile) return <main className="mx-auto mt-24 max-w-md px-4">불러오는 중...</main>;

  return (
    <main className="mx-auto mt-16 max-w-md px-4">
      <h1 className="mb-6 text-2xl font-bold">내 프로필</h1>

      <div className="mb-6 flex items-center gap-4">
        {profile.profileImageUrl
          ? <img src={profile.profileImageUrl} alt="프로필" className="h-20 w-20 rounded-full object-cover" />
          : <div className="flex h-20 w-20 items-center justify-center rounded-full bg-gray-200 text-xs text-gray-500">사진 없음</div>}
        <label className="cursor-pointer rounded border px-3 py-1.5 text-sm">
          {uploading ? '업로드 중...' : '이미지 변경'}
          <input type="file" accept="image/*" className="hidden" onChange={onImageChange} disabled={uploading} />
        </label>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {!editing ? (
        <section className="flex flex-col gap-4">
          <div>
            <h2 className="mb-2 text-sm font-medium text-gray-500">악기</h2>
            {profile.instruments.length > 0
              ? <div className="flex flex-wrap gap-2">{profile.instruments.map((c) => (
                  <span key={c} className="rounded-full bg-indigo-100 px-3 py-1 text-sm text-indigo-800">{labelOf(c)}</span>))}</div>
              : <p className="text-sm text-gray-400">등록된 악기가 없습니다.</p>}
          </div>
          <div>
            <h2 className="mb-2 text-sm font-medium text-gray-500">자기소개</h2>
            <p className="whitespace-pre-wrap text-sm">{profile.bio || <span className="text-gray-400">자기소개가 없습니다.</span>}</p>
          </div>
          <div className="mt-2 flex gap-2">
            <button onClick={startEdit} className="rounded bg-black px-4 py-2 text-white">수정</button>
            <a href="/dashboard" className="rounded border px-4 py-2 text-center">대시보드</a>
          </div>
        </section>
      ) : (
        <section className="flex flex-col gap-4">
          <div>
            <h2 className="mb-2 text-sm font-medium text-gray-500">악기 (최대 {MAX_INSTRUMENTS}개)</h2>
            <div className="flex flex-wrap gap-2">
              {options.map((o) => {
                const on = draftInstruments.includes(o.code);
                return (
                  <button key={o.code} type="button" onClick={() => toggleInstrument(o.code)}
                    className={`rounded-full px-3 py-1 text-sm ${on ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-700'}`}>
                    {o.label}
                  </button>
                );
              })}
            </div>
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-gray-500">자기소개 ({draftBio.length}/500)</label>
            <textarea value={draftBio} maxLength={500} onChange={(e) => setDraftBio(e.target.value)}
              className="h-32 w-full rounded border px-3 py-2 text-sm" />
          </div>
          <div className="mt-2 flex gap-2">
            <button onClick={save} className="rounded bg-black px-4 py-2 text-white">저장</button>
            <button onClick={() => { setEditing(false); setError(null); }} className="rounded border px-4 py-2">취소</button>
          </div>
        </section>
      )}
    </main>
  );
}
