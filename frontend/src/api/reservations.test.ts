import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { aReservation } from '../test/data';
import { RESERVATIONS_PATH } from '../test/handlers';
import { server } from '../test/server';
import { getMyReservations } from './reservations';

describe('내 예매 API', () => {
  it('R6: GET /api/reservations에 저장된 토큰을 Bearer로 붙여 내 예매를 받은 순서대로 돌려준다', async () => {
    saveAccessToken('token-1');
    const reservations = [aReservation({ id: 2 }), aReservation({ id: 1 })];
    let authorization: string | null = 'not-called';
    server.use(
      http.get(RESERVATIONS_PATH, ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json(reservations);
      }),
    );

    await expect(getMyReservations()).resolves.toEqual(reservations);
    expect(authorization).toBe('Bearer token-1');
  });
});
