/** 루트 CLAUDE.md 'API 오류 코드' 표의 code. 백엔드와 같은 목록을 쓴다. */
export const ERROR_CODES = [
  'INVALID_INPUT',
  'INVALID_CREDENTIALS',
  'AUTH_REQUIRED',
  'PAYMENT_FAILED',
  'NOT_FOUND',
  'SEAT_ALREADY_HELD',
  'HOLD_LIMIT_EXCEEDED',
  'HOLD_EXPIRED',
  'INTERNAL_ERROR',
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

/**
 * 응답을 받지 못함(네트워크 오류, 10초 시간 초과). 서버가 보내는 code가 아니라 프론트엔드에서만 쓴다.
 * 결제 화면은 이 값으로 '응답을 받지 못했으니 같은 멱등 키를 다시 쓴다'를 판단한다(spec 7.3, R5).
 */
export const NO_RESPONSE = 'NO_RESPONSE';

export type ApiErrorCode = ErrorCode | typeof NO_RESPONSE;

export function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && (ERROR_CODES as readonly string[]).includes(value);
}

/** API 요청 실패. 응답을 받았으면 status와 code가 있고, 받지 못했으면 status가 null이고 code가 NO_RESPONSE다. */
export class ApiError extends Error {
  readonly status: number | null;
  readonly code: ApiErrorCode;

  constructor(code: ApiErrorCode, status: number | null, options?: ErrorOptions) {
    super(status === null ? code : `${status} ${code}`, options);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}
