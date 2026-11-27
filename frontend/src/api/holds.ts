import { getAccessToken } from '../auth/tokenStorage';
import { apiRequest } from './client';
import { ApiError } from './errors';
import type { Hold } from './types';

/** 선점 요청 본문(spec 6.4) */
export interface CreateHoldRequest {
  performanceId: number;
  /** 고른 좌석의 id(1~4개) */
  seatIds: number[];
}

/**
 * 고른 좌석을 한 번에 선점한다(POST /api/holds, spec 6.4). 로그인이 필요한 API이므로 토큰을 붙인다(auth: true, R6).
 * 이 요청은 멱등하지 않으므로 자동으로 다시 보내지 않는다(다시 보내면 자기 선점과 부딪혀 409가 난다).
 */
export function createHold(request: CreateHoldRequest): Promise<Hold> {
  return apiRequest<Hold>('/api/holds', { method: 'POST', body: request, auth: true });
}

/**
 * 내 선점 조회(GET /api/holds/{holdId}, spec 6.5). 로그인이 필요한 API이므로 토큰을 붙인다(auth: true, R6).
 * holdId는 화면 주소에서 받은 그대로 넘긴다. 경로 한 칸으로만 들어가도록 인코딩한다(getPerformance와 같다).
 * 만료된 선점은 status가 EXPIRED로 오고, 해제·결제된 선점과 다른 사용자의 선점은 404 NOT_FOUND다.
 */
export function getHold(holdId: string): Promise<Hold> {
  return apiRequest<Hold>(`/api/holds/${encodeURIComponent(holdId)}`, { auth: true });
}

/** 내 선점 해제(DELETE /api/holds/{holdId}, spec 6.6). 로그인이 필요한 API이므로 토큰을 붙인다(auth: true, R6). */
export function releaseHold(holdId: string | number): Promise<void> {
  return apiRequest<void>(`/api/holds/${encodeURIComponent(String(holdId))}`, { method: 'DELETE', auth: true });
}

/**
 * 결제 전에 화면을 떠날 때의 선점 해제(spec 7.2·7.3, 결정 0131·0132).
 * - 저장된 토큰이 없으면 보내지 않는다. 로그아웃했거나 401 AUTH_REQUIRED로 토큰이 지워진 경우다.
 *   토큰 없이 보내면 다시 401을 받고, 그 AUTH_REQUIRED가 공통 처리를 불러 로그인 화면으로 보낸다(결정 0043).
 * - 해제가 실패해도(오류 응답, 응답 없음) 넘기지 않는다. 이동은 막지 않고, 선점은 5분 뒤 만료된다.
 *   ApiError가 아닌 예외는 코드 실수이므로 그대로 넘긴다.
 */
export async function releaseHoldOnLeave(holdId: string | number): Promise<void> {
  if (getAccessToken() === null) return;
  try {
    await releaseHold(holdId);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
  }
}
