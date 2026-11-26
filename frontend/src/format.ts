import type { SeatRef } from './api/types';

// 화면에 보여 줄 값의 형식(spec 7.7). API의 시각은 UTC이고 화면에는 한국 시간으로 보여 준다(spec 3장).

const KOREA_TIME_ZONE = 'Asia/Seoul';

const dateTimeParts = new Intl.DateTimeFormat('ko-KR', {
  timeZone: KOREA_TIME_ZONE,
  year: 'numeric',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/**
 * UTC 시각 문자열을 한국 시간 '2026. 12. 20.(일) 19:00' 형식으로 바꾼다.
 * 브라우저 언어 설정마다 다른 기본 형식에 기대지 않도록 각 부분을 받아 직접 이어 붙인다.
 */
export function formatDateTime(isoInstant: string): string {
  const parts = Object.fromEntries(
    dateTimeParts.formatToParts(new Date(isoInstant)).map((part) => [part.type, part.value]),
  ) as Partial<Record<Intl.DateTimeFormatPartTypes, string>>;
  const hour = (parts.hour ?? '').padStart(2, '0');
  const minute = (parts.minute ?? '').padStart(2, '0');
  return `${parts.year}. ${parts.month}. ${parts.day}.(${parts.weekday}) ${hour}:${minute}`;
}

/** 원 단위 금액을 '30,000원' 형식으로 바꾼다. */
export function formatPrice(won: number): string {
  return `${won.toLocaleString('ko-KR')}원`;
}

/** 좌석을 받은 순서대로 'A-1, A-2' 형식으로 이어 붙인다. 좌석 표시 이름은 {row}-{number}다(spec 2.1). */
export function formatSeats(seats: SeatRef[]): string {
  return seats.map((seat) => `${seat.row}-${seat.number}`).join(', ');
}
