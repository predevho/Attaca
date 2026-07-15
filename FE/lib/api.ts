export type BffResult<T = unknown> = { ok: boolean; data?: T; message: string | null };

async function parse<T>(res: Response): Promise<BffResult<T>> {
  try {
    return (await res.json()) as BffResult<T>;
  } catch {
    return { ok: false, message: '응답을 해석할 수 없습니다.' };
  }
}

/** 클라이언트 컴포넌트 전용: same-origin BFF만 호출한다. 토큰은 서버 쿠키에 있으므로 여기서 다루지 않는다. */
export async function postBff<T = unknown>(path: string, body?: unknown): Promise<BffResult<T>> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parse<T>(res);
}

export async function getBff<T = unknown>(path: string): Promise<BffResult<T>> {
  const res = await fetch(path, { method: 'GET' });
  return parse<T>(res);
}
