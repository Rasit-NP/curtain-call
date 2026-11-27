import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { aPaymentResult } from '../test/data';
import { paymentHandler, problem, type PaymentRequestRecord } from '../test/handlers';
import { server } from '../test/server';
import { ApiError } from './errors';
import { createPayment } from './payments';

describe('결제 API', () => {
  it('POST /api/payments에 선점 번호를 명세 모양(holdId)으로, 멱등 키를 Idempotency-Key 헤더로 보내고 결제 결과를 돌려준다', async () => {
    saveAccessToken('token-1');
    const result = aPaymentResult();
    const requests: PaymentRequestRecord[] = [];
    server.use(paymentHandler(() => HttpResponse.json(result, { status: 201 }), requests));

    await expect(createPayment(31, 'key-1')).resolves.toEqual(result);
    expect(requests).toEqual([{ idempotencyKey: 'key-1', body: { holdId: 31 } }]);
  });

  it('R6: 결제 요청에는 저장된 토큰을 Bearer로 붙인다', async () => {
    saveAccessToken('token-1');
    let authorization: string | null = 'not-called';
    server.use(
      http.post('/api/payments', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json(aPaymentResult(), { status: 201 });
      }),
    );

    await createPayment(31, 'key-1');

    expect(authorization).toBe('Bearer token-1');
  });

  it('오류 응답이면 code를 담은 ApiError로 넘긴다', async () => {
    saveAccessToken('token-1');
    server.use(paymentHandler(() => problem(402, 'PAYMENT_FAILED')));

    await expect(createPayment(31, 'key-1')).rejects.toEqual(new ApiError('PAYMENT_FAILED', 402));
  });
});
