import type { ApiErrorCode } from './errors';

/** 사용자에게 보여 줄 안내 문구. docs/spec.md 7.6과 같게 유지한다. */
export const ERROR_MESSAGES: Record<ApiErrorCode, string> = {
  INVALID_INPUT: '입력한 내용을 확인해 주세요.',
  INVALID_CREDENTIALS: '아이디 또는 비밀번호가 올바르지 않습니다.',
  AUTH_REQUIRED: '로그인이 필요합니다.',
  PAYMENT_FAILED: '결제가 승인되지 않았습니다. 다시 시도해 주세요.',
  NOT_FOUND: '요청한 정보를 찾을 수 없습니다.',
  SEAT_ALREADY_HELD: '이미 다른 고객이 선택한 좌석입니다. 다른 좌석을 선택해 주세요.',
  HOLD_LIMIT_EXCEEDED: '한 공연에서 최대 5석까지 예매할 수 있습니다.',
  HOLD_EXPIRED: '좌석 선점 시간(5분)이 지났습니다. 좌석을 다시 선택해 주세요.',
  INTERNAL_ERROR: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  NO_RESPONSE: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
};

export function errorMessage(code: ApiErrorCode): string {
  return ERROR_MESSAGES[code];
}

/**
 * 요청한 화면에 문구를 그릴 code인지. AUTH_REQUIRED는 공통 처리가 로그인 화면으로 보내며 그 화면에만 보여 주므로(spec 3.3·7.6),
 * 요청한 화면에는 그리지 않는다. 그리면 로그인 화면으로 가기 전에 잠깐 보이고, 화면 읽기 프로그램이 두 번 읽는다(결정 0132).
 */
export function isShownOnScreen(code: ApiErrorCode): boolean {
  return code !== 'AUTH_REQUIRED';
}
