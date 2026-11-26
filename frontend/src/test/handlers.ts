import { http, HttpResponse } from 'msw';
import type { PerformanceSummary, Reservation } from '../api/types';

// API별 MSW 핸들러. server.ts는 기본 핸들러를 두지 않으므로 테스트가 server.use(...)로 필요한 것만 더한다.

export const PERFORMANCES_PATH = '/api/performances';
export const RESERVATIONS_PATH = '/api/reservations';

/** 오류 응답(spec 3.1의 ProblemDetail + code) */
export function problem(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } });
}

/** GET /api/performances 공연 목록(spec 6.2) */
export function performancesHandler(performances: PerformanceSummary[]) {
  return http.get(PERFORMANCES_PATH, () => HttpResponse.json(performances));
}

/** GET /api/reservations 내 예매(spec 6.8). 토큰이 없으면 서버처럼 401 AUTH_REQUIRED를 준다. */
export function reservationsHandler(reservations: Reservation[]) {
  return http.get(RESERVATIONS_PATH, ({ request }) =>
    request.headers.get('Authorization') === null ? problem(401, 'AUTH_REQUIRED') : HttpResponse.json(reservations),
  );
}
