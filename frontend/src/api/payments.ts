import { apiRequest } from './client';
import type { PaymentResult } from './types';

/**
 * 선점 1건을 결제한다(POST /api/payments, spec 6.7). 로그인이 필요한 API이므로 토큰을 붙인다(auth: true, R6).
 * idempotencyKey는 Idempotency-Key 헤더로 보낸다. 같은 키로 다시 보내면 서버가 처음 결과를 그대로 돌려준다(R5, spec 6.7.1).
 * 키를 새로 만들지 다시 쓸지는 화면이 정한다(spec 7.3). 이 함수는 자동으로 다시 보내지 않는다.
 */
export function createPayment(holdId: number, idempotencyKey: string): Promise<PaymentResult> {
  return apiRequest<PaymentResult>('/api/payments', {
    method: 'POST',
    body: { holdId },
    auth: true,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}
