import 'server-only';
import { unwrap, type ApiResponse } from '@/lib/unwrap';

const BE_BASE_URL = process.env.BE_BASE_URL ?? 'http://localhost:8080';

export type BeResult = { ok: boolean; status: number; data: unknown; message: string | null };

/** Spring BE를 호출하고 ApiResponse를 {ok,status,data,message}로 정규화한다. 쿠키/next 헤더를 모른다(순수 fetch). */
export async function beFetch(path: string, init?: RequestInit): Promise<BeResult> {
  const isForm = init?.body instanceof FormData;
  const baseHeaders: Record<string, string> = isForm ? {} : { 'content-type': 'application/json' };
  let res: Response;
  try {
    res = await fetch(BE_BASE_URL + path, {
      ...init,
      headers: { ...baseHeaders, ...(init?.headers ?? {}) },
    });
  } catch {
    return { ok: false, status: 0, data: null, message: '서버에 연결할 수 없습니다.' };
  }

  let body: ApiResponse<unknown>;
  try {
    body = (await res.json()) as ApiResponse<unknown>;
  } catch {
    return { ok: false, status: res.status, data: null, message: '서버 응답을 해석할 수 없습니다.' };
  }

  const { ok, data, message } = unwrap(body);
  return { ok, status: res.status, data, message };
}
