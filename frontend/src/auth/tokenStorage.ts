// 액세스 토큰은 여기서만 localStorage에 읽고 쓴다(frontend/CLAUDE.md). ESLint가 다른 폴더의 접근을 막는다.

export const ACCESS_TOKEN_KEY = 'curtain-call.accessToken';

type Listener = () => void;

const listeners = new Set<Listener>();

export function saveAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
  notify();
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  notify();
}

/**
 * 이 앱에서 토큰을 저장하거나 지울 때(로그인, 로그아웃, 401 AUTH_REQUIRED) 부를 함수를 등록하고, 등록을 끊는 함수를 돌려준다.
 * 공통 헤더가 '로그인'·'로그아웃'을 바로 바꾸는 데 쓴다(useAccessToken).
 */
export function subscribeAccessToken(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function notify(): void {
  for (const listener of [...listeners]) {
    listener();
  }
}
