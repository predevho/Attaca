import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import LoginPage from '@/app/(auth)/login/page';

describe('LoginPage', () => {
  it('아이디/비밀번호 입력과 로그인 버튼을 렌더링한다', () => {
    render(<LoginPage />);
    expect(screen.getByLabelText('아이디')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });
});
