import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import { LoginForm } from '@/app/(auth)/login/LoginForm';
import { ERROR_MESSAGES } from '@/app/(auth)/login/page';

describe('LoginForm', () => {
  it('아이디/비밀번호 입력과 로그인 버튼을 렌더링한다', () => {
    render(<LoginForm initialError={null} />);
    expect(screen.getByLabelText('아이디')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });

  it('카카오 로그인 링크가 start 라우트를 가리킨다', () => {
    render(<LoginForm initialError={null} />);
    const kakao = screen.getByRole('link', { name: '카카오 로그인' });
    expect(kakao).toHaveAttribute('href', '/api/bff/oauth/kakao/start');
  });

  it('initialError를 화면에 표시한다', () => {
    render(<LoginForm initialError="카카오 로그인이 취소되었습니다." />);
    expect(screen.getByText('카카오 로그인이 취소되었습니다.')).toBeInTheDocument();
  });
});

describe('ERROR_MESSAGES', () => {
  it('알려진 error 코드에 한글 메시지를 매핑한다', () => {
    expect(ERROR_MESSAGES.state).toBeTruthy();
    expect(ERROR_MESSAGES.kakao_cancelled).toBe('카카오 로그인이 취소되었습니다.');
    expect(ERROR_MESSAGES.oauth).toBeTruthy();
    expect(ERROR_MESSAGES.oauth_config).toBeTruthy();
  });
});
