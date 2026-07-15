export type ApiError = { resultCode: string; code: string; message: string };
export type ApiResponse<T> = { success: boolean; data: T | null; error: ApiError | null };

export function unwrap<T>(res: ApiResponse<T>): { ok: boolean; data: T | null; message: string | null } {
  if (res.success) return { ok: true, data: res.data, message: null };
  return { ok: false, data: null, message: res.error?.message ?? '알 수 없는 오류가 발생했습니다.' };
}
