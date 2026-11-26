import { clearAccessToken, getAccessToken } from './tokenStorage';

type Listener = () => void;

const listeners = new Set<Listener>();

/**
 * 401 AUTH_REQUIRED를 받았을 때 부를 함수를 등록하고, 등록을 끊는 함수를 돌려준다.
 * 라우터 안의 AuthRequiredRedirect가 등록해 로그인 화면으로 보낸다.
 */
export function onAuthRequired(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * 401 AUTH_REQUIRED 공통 처리(spec 3.3). 저장된 토큰을 지우고 등록된 함수에 알린다.
 * API 클라이언트가 응답의 code가 AUTH_REQUIRED일 때만 부른다. 로그인 실패(INVALID_CREDENTIALS) 같은 다른 401에는 부르지 않는다(결정 0008).
 *
 * sentToken은 그 요청에 붙여 보낸 토큰(없으면 null)이다. 지금 저장된 토큰과 다르면 요청을 보낸 뒤 다시 로그인한 것이므로,
 * 늦게 온 이 응답으로 새 토큰을 지우거나 로그인 화면으로 다시 보내지 않는다(결정 0043).
 */
export function handleAuthRequired(sentToken: string | null): void {
  if (getAccessToken() !== sentToken) return;
  clearAccessToken();
  for (const listener of [...listeners]) {
    listener();
  }
}
