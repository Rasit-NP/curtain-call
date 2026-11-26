import type { Hold, PerformanceDetail, PerformanceSummary, Reservation, Seat, SeatRef } from '../api/types';

// 테스트 데이터 생성 함수. 테스트마다 필요한 값만 덮어써서 만든다(spec 5 응답 객체).

export function aPerformance(overrides: Partial<PerformanceSummary> = {}): PerformanceSummary {
  return {
    id: 1,
    title: '작은 방의 햄릿',
    venue: '대학로 커튼홀',
    startsAt: '2026-12-20T10:00:00Z',
    price: 30000,
    ...overrides,
  };
}

export function aSeat(overrides: Partial<Seat> = {}): Seat {
  return { id: 101, row: 'A', number: 1, gridRow: 1, gridColumn: 1, status: 'AVAILABLE', ...overrides };
}

export function aPerformanceDetail(overrides: Partial<PerformanceDetail> = {}): PerformanceDetail {
  return { ...aPerformance(), seats: [aSeat()], ...overrides };
}

export function aSeatRef(overrides: Partial<SeatRef> = {}): SeatRef {
  return { id: 101, row: 'A', number: 1, ...overrides };
}

export function aHold(overrides: Partial<Hold> = {}): Hold {
  return {
    id: 31,
    status: 'ACTIVE',
    performance: aPerformance(),
    seats: [aSeatRef()],
    amount: 30000,
    expiresAt: '2026-12-01T10:05:00Z',
    ...overrides,
  };
}

export function aReservation(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: 1,
    performance: aPerformance(),
    seats: [aSeatRef()],
    amount: 30000,
    reservedAt: '2026-12-01T03:00:00Z',
    ...overrides,
  };
}
