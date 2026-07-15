import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

const getBff = vi.fn();
const putBff = vi.fn();
const putBffForm = vi.fn();
vi.mock('@/lib/api', () => ({ getBff: (...a: unknown[]) => getBff(...a), putBff: (...a: unknown[]) => putBff(...a), putBffForm: (...a: unknown[]) => putBffForm(...a) }));

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
    const editBtn = await screen.findByRole('button', { name: '수정' });
    fireEvent.click(editBtn);
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument());
    // 선택지 칩이 렌더된다
    expect(screen.getByRole('button', { name: '첼로' })).toBeInTheDocument();
  });
});
