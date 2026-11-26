// 액세스 토큰은 여기서만 localStorage에 읽고 쓴다(frontend/CLAUDE.md). ESLint가 다른 폴더의 접근을 막는다.

export const ACCESS_TOKEN_KEY = 'curtain-call.accessToken';

export function saveAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}
