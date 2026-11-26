import { apiRequest } from './client';
import type { Reservation } from './types';

/**
 * 내 예매 목록(GET /api/reservations, spec 6.8). 최신순(reservedAt 내림차순)이다.
 * 로그인이 필요한 API이므로 저장된 토큰을 붙인다(auth: true, R6).
 */
export function getMyReservations(): Promise<Reservation[]> {
  return apiRequest<Reservation[]>('/api/reservations', { auth: true });
}
