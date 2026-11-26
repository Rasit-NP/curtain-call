import { useSyncExternalStore } from 'react';
import { getAccessToken, subscribeAccessToken } from './tokenStorage';

/** 지금 저장된 액세스 토큰. 로그인·로그아웃·401 AUTH_REQUIRED로 토큰이 바뀌면 다시 그린다. */
export function useAccessToken(): string | null {
  return useSyncExternalStore(subscribeAccessToken, getAccessToken);
}
