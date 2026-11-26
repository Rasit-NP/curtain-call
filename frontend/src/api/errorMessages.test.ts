import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { errorMessage, ERROR_MESSAGES } from './errorMessages';
import { ERROR_CODES, NO_RESPONSE } from './errors';

// docs/spec.md 7.6의 표를 그대로 옮긴다. 문구를 바꾸려면 명세부터 고친다.
const SPEC_MESSAGES = [
  ['INVALID_INPUT', '입력한 내용을 확인해 주세요.'],
  ['INVALID_CREDENTIALS', '아이디 또는 비밀번호가 올바르지 않습니다.'],
  ['AUTH_REQUIRED', '로그인이 필요합니다.'],
  ['PAYMENT_FAILED', '결제가 승인되지 않았습니다. 다시 시도해 주세요.'],
  ['NOT_FOUND', '요청한 정보를 찾을 수 없습니다.'],
  ['SEAT_ALREADY_HELD', '이미 다른 고객이 선택한 좌석입니다. 다른 좌석을 선택해 주세요.'],
  ['HOLD_LIMIT_EXCEEDED', '한 공연에서 최대 4석까지 예매할 수 있습니다.'],
  ['HOLD_EXPIRED', '좌석 선점 시간(5분)이 지났습니다. 좌석을 다시 선택해 주세요.'],
  ['INTERNAL_ERROR', '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
  [NO_RESPONSE, '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
] as const;

/** 루트 CLAUDE.md 'API 오류 코드' 표의 code 목록 */
function codesInRootClaudeMd(): string[] {
  // jsdom 환경에서는 import.meta.url이 file: URL이 아니므로, vitest를 실행하는 frontend/ 기준으로 찾는다.
  const text = readFileSync(resolve(process.cwd(), '../CLAUDE.md'), 'utf8');
  const section = text.split('## API 오류 코드')[1].split('\n## ')[0];
  return [...section.matchAll(/^\| \d{3} \| `([A-Z_]+)` \|/gm)].map((match) => match[1]);
}

describe('안내 문구(spec 7.6)', () => {
  it.each(SPEC_MESSAGES)('%s의 문구는 명세와 같다', (code, message) => {
    expect(errorMessage(code)).toBe(message);
  });

  it('루트 CLAUDE.md 오류 코드 표의 모든 code와 응답 없음에 문구가 있다', () => {
    const tableCodes = codesInRootClaudeMd();

    expect(tableCodes.length).toBeGreaterThan(0);
    expect([...ERROR_CODES].sort()).toEqual([...tableCodes].sort());
    expect(Object.keys(ERROR_MESSAGES).sort()).toEqual([...tableCodes, NO_RESPONSE].sort());
  });
});
