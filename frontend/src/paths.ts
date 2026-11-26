// 앱 화면 경로(spec 7.1). 로그인 화면 경로는 src/auth/loginRedirect.ts의 LOGIN_PATH다.

/** 공연 목록 */
export const HOME_PATH = '/';

/** 내 예매(로그인 필요) */
export const RESERVATIONS_PATH = '/reservations';

/** 좌석 선택(로그인 필요) 라우트 경로 */
export const PERFORMANCE_PATH = '/performances/:performanceId';

/** 좌석 선택(로그인 필요) */
export function performancePath(performanceId: number): string {
  return `/performances/${performanceId}`;
}

/** 결제·예매 확인(로그인 필요). 화면은 4단계에서 만든다. */
export function holdPath(holdId: number): string {
  return `/holds/${holdId}`;
}
