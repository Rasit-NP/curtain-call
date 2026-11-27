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

/** 좌석의 API 상태(spec 2.2). 만료된 선점의 좌석은 AVAILABLE로 온다. */
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'SOLD';

/** 공연 상세의 좌석(spec 5). 표시 이름은 row·number, 배치 격자 위치는 gridRow·gridColumn(1부터, spec 2.1 '좌석 위치')이다. */
export interface Seat {
  id: number;
  row: string;
  number: number;
  /** 격자의 몇 번째 줄인지. 1번째 줄이 무대 쪽이다. */
  gridRow: number;
  /** 격자의 몇 번째 칸인지 */
  gridColumn: number;
  status: SeatStatus;
}

/** 공연 상세(GET /api/performances/{id}, spec 6.3). 좌석은 row, number 오름차순이다. */
export interface PerformanceDetail extends PerformanceSummary {
  seats: Seat[];
}

export interface SeatRef {
  id: number;
  row: string;
  number: number;
}

/** 선점(spec 5). 유효하면 ACTIVE, 만료됐으면 EXPIRED다. */
export interface Hold {
  id: number;
  status: 'ACTIVE' | 'EXPIRED';
  performance: PerformanceSummary;
  /** row, number 오름차순 */
  seats: SeatRef[];
  /** 결제할 금액(원) */
  amount: number;
  /** 만료 시각(UTC). 이 시각과 같거나 지나면 만료다(R3). */
  expiresAt: string;
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

/** 결제 승인 응답(POST /api/payments의 201, spec 6.7) */
export interface PaymentResult {
  paymentId: number;
  /** 결제 금액(원) */
  amount: number;
  /** 결제로 만든 예매 */
  reservation: Reservation;
}
