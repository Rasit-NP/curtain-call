import { describe, expect, it } from 'vitest';
import { formatDateTime, formatPrice, formatSeats, seatName } from './format';

describe('일시 형식', () => {
  it('UTC 시각을 한국 시간으로 바꿔 "2026. 12. 20.(일) 19:00" 형식으로 보여 준다', () => {
    expect(formatDateTime('2026-12-20T10:00:00Z')).toBe('2026. 12. 20.(일) 19:00');
  });

  it('한국 시간으로 자정을 넘으면 다음 날로 보여 주고, 월·일은 앞에 0을 붙이지 않는다', () => {
    expect(formatDateTime('2027-01-15T15:30:00Z')).toBe('2027. 1. 16.(토) 00:30');
  });

  it('한국 시간으로 자정 1분 전이면 같은 날이다', () => {
    expect(formatDateTime('2027-01-15T14:59:00Z')).toBe('2027. 1. 15.(금) 23:59');
  });

  it('한국 시간으로 해가 바뀌는 경계도 다음 해로 보여 준다', () => {
    expect(formatDateTime('2026-12-31T15:00:00Z')).toBe('2027. 1. 1.(금) 00:00');
  });
});

describe('가격 형식', () => {
  it.each([
    [30000, '30,000원'],
    [0, '0원'],
    [1234567, '1,234,567원'],
  ])('%i원은 "%s"로 보여 준다', (price, expected) => {
    expect(formatPrice(price)).toBe(expected);
  });
});

describe('좌석 형식', () => {
  it('좌석 표시 이름은 줄과 번호를 붙여 쓴다(A3, B10)', () => {
    expect(seatName({ row: 'A', number: 3 })).toBe('A3');
    expect(seatName({ row: 'B', number: 10 })).toBe('B10');
  });

  it('좌석을 받은 순서대로 "A1, A2"처럼 보여 준다', () => {
    expect(
      formatSeats([
        { id: 1, row: 'A', number: 1 },
        { id: 2, row: 'A', number: 2 },
        { id: 3, row: 'B', number: 10 },
      ]),
    ).toBe('A1, A2, B10');
  });
});
