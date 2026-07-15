import { LoginForm } from './LoginForm';

/** OAuth/로그인 실패 코드 → 사용자 안내 문구. */
export const ERROR_MESSAGES: Record<string, string> = {
  state: '보안 검증에 실패했습니다. 다시 시도해 주세요.',
  kakao_cancelled: '카카오 로그인이 취소되었습니다.',
  oauth: '카카오 로그인에 실패했습니다.',
  oauth_config: '카카오 로그인이 현재 설정되지 않았습니다.',
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  const initialError = error ? (ERROR_MESSAGES[error] ?? '로그인에 실패했습니다.') : null;
  return <LoginForm initialError={initialError} />;
}
