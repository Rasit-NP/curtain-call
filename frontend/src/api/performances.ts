import { apiRequest } from './client';
import type { PerformanceDetail, PerformanceSummary } from './types';

/**
 * 공연 목록(GET /api/performances, spec 6.2). startsAt 오름차순이다.
 * 공개 API이므로 저장된 토큰을 붙이지 않는다(auth: false, spec 3장, R6). 만료 토큰을 붙이면 공개 API도 401이 난다.
 */
export function getPerformances(): Promise<PerformanceSummary[]> {
  return apiRequest<PerformanceSummary[]>('/api/performances', { auth: false });
}

/**
 * 공연 상세와 좌석(GET /api/performances/{performanceId}, spec 6.3). 공개 API이므로 토큰을 붙이지 않는다(auth: false, R6).
 * performanceId는 화면 주소에서 받은 그대로 넘긴다. 숫자가 아니면 서버가 400 INVALID_INPUT을 준다.
 * 경로 한 칸으로만 들어가도록 인코딩해, '/'나 '?'가 섞여도 다른 API 경로나 쿼리가 되지 않게 한다.
 */
export function getPerformance(performanceId: string): Promise<PerformanceDetail> {
  return apiRequest<PerformanceDetail>(`/api/performances/${encodeURIComponent(performanceId)}`, { auth: false });
}
