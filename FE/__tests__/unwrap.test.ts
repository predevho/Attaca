import { describe, it, expect } from 'vitest';
import { unwrap } from '@/lib/unwrap';

describe('unwrap', () => {
  it('성공 응답을 벗긴다', () => {
    expect(unwrap({ success: true, data: { id: 1 }, error: null }))
      .toEqual({ ok: true, data: { id: 1 }, message: null });
  });

  it('실패 응답에서 메시지를 뽑는다', () => {
    expect(unwrap({
      success: false, data: null,
      error: { resultCode: '401-07', code: 'LOGIN_FAILED', message: '아이디 또는 비밀번호가 올바르지 않습니다.' },
    })).toEqual({ ok: false, data: null, message: '아이디 또는 비밀번호가 올바르지 않습니다.' });
  });
});
