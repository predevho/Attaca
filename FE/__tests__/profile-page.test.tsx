import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';

const push = vi.fn();
const router = { push };
vi.mock('next/navigation', () => ({ useRouter: () => router }));

const getBff = vi.fn();
const putBff = vi.fn();
const putBffForm = vi.fn();
vi.mock('@/lib/api', () => ({
  getBff: (...a: unknown[]) => getBff(...a),
  putBff: (...a: unknown[]) => putBff(...a),
  putBffForm: (...a: unknown[]) => putBffForm(...a),
}));

import ProfilePage from '@/app/profile/page';

beforeEach(() => {
  vi.clearAllMocks();
  getBff.mockImplementation(async (path: string) => {
    if (path === '/api/bff/me') return { ok: true, data: { instruments: ['VIOLIN'], bio: '첼로 좋아요', profileImageUrl: null }, message: null };
    if (path === '/api/bff/profile-options') return { ok: true, data: { instruments: [{ code: 'VIOLIN', label: '바이올린' }, { code: 'CELLO', label: '첼로' }] }, message: null };
    return { ok: false, message: 'x' };
  });
});

describe('ProfilePage', () => {
  it('조회 모드에서 악기 label과 자기소개를 보여준다', async () => {
    render(<ProfilePage />);
    expect(await screen.findByText('바이올린')).toBeInTheDocument();
    expect(screen.getByText('첼로 좋아요')).toBeInTheDocument();
  });

  it('수정 버튼을 누르면 편집 모드로 전환된다', async () => {
    render(<ProfilePage />);
    fireEvent.click(await screen.findByRole('button', { name: '수정' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '첼로' })).toBeInTheDocument();
  });

  it('저장하면 조회 모드에 반영된다', async () => {
    putBff.mockResolvedValue({ ok: true, data: { instruments: ['VIOLIN', 'CELLO'], bio: '수정됨', profileImageUrl: null }, message: null });
    render(<ProfilePage />);
    fireEvent.click(await screen.findByRole('button', { name: '수정' }));
    fireEvent.click(await screen.findByRole('button', { name: '첼로' }));
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() => expect(putBff).toHaveBeenCalledWith('/api/bff/me/profile', expect.objectContaining({ bio: '첼로 좋아요' })));
    expect(await screen.findByText('수정됨')).toBeInTheDocument();
  });

  it('악기 10개 초과 선택은 막고 안내한다', async () => {
    const many = Array.from({ length: 12 }, (_, i) => ({ code: `I${i}`, label: `악기${i}` }));
    getBff.mockImplementation(async (path: string) => {
      if (path === '/api/bff/me') return { ok: true, data: { instruments: [], bio: null, profileImageUrl: null }, message: null };
      if (path === '/api/bff/profile-options') return { ok: true, data: { instruments: many }, message: null };
      return { ok: false, message: 'x' };
    });
    render(<ProfilePage />);
    fireEvent.click(await screen.findByRole('button', { name: '수정' }));
    for (let i = 0; i < 11; i++) fireEvent.click(screen.getByRole('button', { name: `악기${i}` }));
    expect(await screen.findByText(/최대 10개까지 선택할 수 있습니다/)).toBeInTheDocument();
  });

  it('이미지가 아닌 파일은 업로드하지 않고 에러를 표시한다', async () => {
    render(<ProfilePage />);
    await screen.findByRole('button', { name: '수정' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['x'], 'a.txt', { type: 'text/plain' });
    fireEvent.change(input, { target: { files: [file] } });
    expect(putBffForm).not.toHaveBeenCalled();
    expect(await screen.findByText('이미지 파일만 업로드할 수 있습니다.')).toBeInTheDocument();
  });

  it('me 조회 실패면 /login으로 리다이렉트한다', async () => {
    getBff.mockImplementation(async (path: string) => {
      if (path === '/api/bff/me') return { ok: false, message: '401' };
      return { ok: true, data: { instruments: [] }, message: null };
    });
    render(<ProfilePage />);
    await waitFor(() => expect(push).toHaveBeenCalledWith('/login'));
  });
});
