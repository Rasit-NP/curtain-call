import { apiRequest } from './client';
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
