import { apiRequest } from './client';
import type { PerformanceSummary } from './types';

/**
 * 공연 목록(GET /api/performances, spec 6.2). startsAt 오름차순이다.
 * 공개 API이므로 저장된 토큰을 붙이지 않는다(auth: false, spec 3장, R6). 만료 토큰을 붙이면 공개 API도 401이 난다.
 */
export function getPerformances(): Promise<PerformanceSummary[]> {
  return apiRequest<PerformanceSummary[]>('/api/performances', { auth: false });
}
