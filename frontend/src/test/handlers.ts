import { http, HttpResponse } from 'msw';
import type { Hold, PerformanceDetail, PerformanceSummary, Reservation } from '../api/types';

// API별 MSW 핸들러. server.ts는 기본 핸들러를 두지 않으므로 테스트가 server.use(...)로 필요한 것만 더한다.

export const PERFORMANCES_PATH = '/api/performances';
export const PERFORMANCE_DETAIL_PATH = '/api/performances/:performanceId';
export const HOLDS_PATH = '/api/holds';
export const RESERVATIONS_PATH = '/api/reservations';

/** 오류 응답(spec 3.1의 ProblemDetail + code) */
export function problem(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } });
}

/** GET /api/performances 공연 목록(spec 6.2) */
export function performancesHandler(performances: PerformanceSummary[]) {
  return http.get(PERFORMANCES_PATH, () => HttpResponse.json(performances));
}

/** GET /api/performances/{performanceId} 공연 상세와 좌석(spec 6.3). 다른 공연 번호면 서버처럼 404 NOT_FOUND를 준다. */
export function performanceDetailHandler(detail: PerformanceDetail) {
  return http.get(PERFORMANCE_DETAIL_PATH, ({ params }) =>
    params.performanceId === String(detail.id) ? HttpResponse.json(detail) : problem(404, 'NOT_FOUND'),
  );
}

/** POST /api/holds 선점 요청 본문(spec 6.4) */
export interface HoldRequestBody {
  performanceId: number;
  seatIds: number[];
}

/**
 * POST /api/holds 좌석 묶음 선점(spec 6.4). 토큰이 없으면 서버처럼 401 AUTH_REQUIRED, 있으면 201과 hold를 준다.
 * 받은 요청 본문을 requests에 쌓아 테스트가 확인할 수 있게 한다.
 */
export function holdCreatedHandler(hold: Hold, requests: HoldRequestBody[] = []) {
  return http.post(HOLDS_PATH, async ({ request }) => {
    if (request.headers.get('Authorization') === null) return problem(401, 'AUTH_REQUIRED');
    requests.push((await request.json()) as HoldRequestBody);
    return HttpResponse.json(hold, { status: 201 });
  });
}

/** GET /api/reservations 내 예매(spec 6.8). 토큰이 없으면 서버처럼 401 AUTH_REQUIRED를 준다. */
export function reservationsHandler(reservations: Reservation[]) {
  return http.get(RESERVATIONS_PATH, ({ request }) =>
    request.headers.get('Authorization') === null ? problem(401, 'AUTH_REQUIRED') : HttpResponse.json(reservations),
  );
}
