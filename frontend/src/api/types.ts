// API 응답 객체(spec 5). 같은 이름의 객체는 모든 응답에서 모양이 같다.
// 시각은 ISO 8601 UTC 문자열, 금액은 원 단위 정수다(spec 3장).

export interface PerformanceSummary {
  id: number;
  title: string;
  venue: string;
  /** 공연 시작 시각(UTC). 화면에는 한국 시간으로 보여 준다. */
  startsAt: string;
  /** 1석 가격(원) */
  price: number;
}

export interface SeatRef {
  id: number;
  row: string;
  number: number;
}

export interface Reservation {
  id: number;
  performance: PerformanceSummary;
  /** row, number 오름차순 */
  seats: SeatRef[];
  /** 결제 금액(원) */
  amount: number;
  reservedAt: string;
}
