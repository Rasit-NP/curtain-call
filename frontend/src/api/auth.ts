import { apiRequest } from './client';

export interface LoginRequest {
  username: string;
  password: string;
}

/** POST /api/auth/login의 200 응답(spec 6.1). expiresIn은 초 단위다. */
export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
}

/**
 * 로그인한다. 공개 API이므로 저장된 토큰을 붙이지 않는다(auth: false).
 * 실패는 ApiError로 넘긴다: 400 INVALID_INPUT, 401 INVALID_CREDENTIALS, 응답 없음 등.
 */
export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/api/auth/login', { method: 'POST', body: request, auth: false });
}
