import { getAccessToken } from '../auth/tokenStorage';
import { ApiError, isErrorCode, NO_RESPONSE } from './errors';

/** 응답을 기다리는 최대 시간. 넘으면 요청을 끊고 응답 없음으로 본다(spec 7.5). */
export const REQUEST_TIMEOUT_MS = 10_000;

/** API 경로. 개발 서버 프록시를 거치도록 /api로 시작하는 상대 경로만 받는다. */
export type ApiPath = `/api/${string}`;

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  /** JSON으로 보낼 본문 */
  body?: unknown;
  /**
   * 로그인이 필요한 API인지. true면 저장된 토큰을 Authorization 헤더로 붙인다.
   * 공개 API에 토큰을 붙이면 잘못되거나 만료된 토큰 때문에 401이 날 수 있어 붙이지 않는다(spec 3장, R6).
   * 기본값을 두지 않아 호출하는 쪽이 매번 정하게 한다.
   */
  auth: boolean;
  headers?: Record<string, string>;
}

/**
 * API를 호출한다. fetch는 여기서만 부른다.
 * - 성공(2xx): JSON 본문을 돌려준다. 본문이 없으면(204) undefined.
 * - 오류 응답: ApiError(status, code). 본문에 오류 코드 표의 code가 없으면 INTERNAL_ERROR로 본다.
 * - 응답 없음(네트워크 오류, 10초 시간 초과): ApiError(null, NO_RESPONSE)
 */
export async function apiRequest<T>(path: ApiPath, options: ApiRequestOptions): Promise<T> {
  if (!path.startsWith('/api/')) {
    throw new Error(`API 경로는 /api/로 시작하는 상대 경로여야 합니다: ${path}`);
  }

  const headers = new Headers(options.headers);
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (options.auth) {
    const token = getAccessToken();
    if (token !== null) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    let response: Response;
    try {
      response = await fetch(path, {
        method: options.method ?? 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal,
      });
    } catch (cause) {
      throw new ApiError(NO_RESPONSE, null, { cause });
    }

    if (!response.ok) {
      throw new ApiError(await errorCodeOf(response), response.status);
    }
    return (await successBodyOf(response)) as T;
  } finally {
    clearTimeout(timer);
  }
}

async function errorCodeOf(response: Response) {
  const body = await readJson(response);
  const code = typeof body === 'object' && body !== null ? (body as { code?: unknown }).code : undefined;
  return isErrorCode(code) ? code : 'INTERNAL_ERROR';
}

async function successBodyOf(response: Response): Promise<unknown> {
  const text = await readText(response);
  if (text === '') return undefined;
  try {
    return JSON.parse(text);
  } catch (cause) {
    throw new ApiError('INTERNAL_ERROR', response.status, { cause });
  }
}

async function readJson(response: Response): Promise<unknown> {
  const text = await readText(response);
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

/** 본문을 읽는 중에 연결이 끊기거나 시간 제한에 걸리면 응답 없음으로 본다. */
async function readText(response: Response): Promise<string> {
  try {
    return await response.text();
  } catch (cause) {
    throw new ApiError(NO_RESPONSE, null, { cause });
  }
}
