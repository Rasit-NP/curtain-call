import type { Location } from 'react-router';

/** 로그인 화면 경로(spec 7.1) */
export const LOGIN_PATH = '/login';

/** 로그인 뒤 돌아갈 화면이 없을 때 가는 곳. 공연 목록이다(spec 7.1). */
export const DEFAULT_RETURN_PATH = '/';

/** 로그인 화면이 보여 줄 안내. 지금은 AUTH_REQUIRED(로그인이 필요합니다)뿐이다(spec 7.6). */
export type LoginNotice = 'AUTH_REQUIRED';

/** 로그인 화면으로 보낼 때 라우터의 location state에 담는 값 */
export interface LoginRedirectState {
  /** 로그인 뒤 돌아갈 앱 안의 경로(쿼리·해시 포함) */
  from: string;
  notice?: LoginNotice;
}

/** 지금 화면(location)으로 돌아오도록 로그인 화면에 넘길 state를 만든다. */
export function loginRedirectState(
  location: Pick<Location, 'pathname' | 'search' | 'hash'>,
  notice?: LoginNotice,
): LoginRedirectState {
  const from = `${location.pathname}${location.search}${location.hash}`;
  return notice === undefined ? { from } : { from, notice };
}

/**
 * 로그인 뒤 돌아갈 경로. state에 앱 안의 경로가 없으면 공연 목록(/)이다.
 * '//host'나 '/\host'는 브라우저가 다른 사이트 주소로 읽을 수 있어 받지 않는다.
 */
export function returnPathOf(state: unknown): string {
  const from = isRecord(state) ? state.from : undefined;
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//') || from.startsWith('/\\')) {
    return DEFAULT_RETURN_PATH;
  }
  return from;
}

/** 로그인 화면이 처음에 보여 줄 안내. 없으면 undefined */
export function noticeOf(state: unknown): LoginNotice | undefined {
  return isRecord(state) && state.notice === 'AUTH_REQUIRED' ? 'AUTH_REQUIRED' : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
