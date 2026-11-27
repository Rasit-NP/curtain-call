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

/** 결제·예매 확인(로그인 필요) 라우트 경로 */
export const HOLD_PATH = '/holds/:holdId';

/** 결제·예매 확인(로그인 필요) */
export function holdPath(holdId: number): string {
  return `/holds/${holdId}`;
}

/**
 * 좌석 선택 화면이 결제 화면으로 보낼 때 라우터 기록 state에 담는 값.
 * 결제 화면의 '뒤로 가기'는 이 표시가 있을 때만 기록을 되돌린다(앞 기록이 그 좌석 선택 화면이다, spec 7.3).
 */
export const FROM_SEAT_SELECT_STATE = { fromSeatSelect: true } as const;

/** 라우터 기록 state가 좌석 선택 화면에서 왔다는 표시인지 */
export function isFromSeatSelect(state: unknown): boolean {
  return typeof state === 'object' && state !== null && (state as { fromSeatSelect?: unknown }).fromSeatSelect === true;
}
