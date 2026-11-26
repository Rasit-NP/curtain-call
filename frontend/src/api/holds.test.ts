import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { aHold } from '../test/data';
import { holdCreatedHandler, type HoldRequestBody } from '../test/handlers';
import { server } from '../test/server';
import { ApiError } from './errors';
import { createHold } from './holds';

describe('선점 API', () => {
  it('POST /api/holds에 공연 번호와 좌석 번호를 명세 모양(performanceId, seatIds)으로 보내고 받은 선점을 돌려준다', async () => {
    saveAccessToken('token-1');
    const hold = aHold({ id: 31 });
    const requests: HoldRequestBody[] = [];
    server.use(holdCreatedHandler(hold, requests));

    await expect(createHold({ performanceId: 1, seatIds: [101, 104] })).resolves.toEqual(hold);
    expect(requests).toEqual([{ performanceId: 1, seatIds: [101, 104] }]);
  });

  it('R6: 선점 요청에는 저장된 토큰을 Bearer로 붙인다', async () => {
    saveAccessToken('token-1');
    let authorization: string | null = 'not-called';
    server.use(
      http.post('/api/holds', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json(aHold(), { status: 201 });
      }),
    );

    await createHold({ performanceId: 1, seatIds: [101] });

    expect(authorization).toBe('Bearer token-1');
  });

  it('오류 응답이면 code를 담은 ApiError로 넘긴다', async () => {
    saveAccessToken('token-1');
    server.use(
      http.post('/api/holds', () =>
        HttpResponse.json({ status: 409, code: 'SEAT_ALREADY_HELD' }, { status: 409 }),
      ),
    );

    await expect(createHold({ performanceId: 1, seatIds: [101] })).rejects.toEqual(
      new ApiError('SEAT_ALREADY_HELD', 409),
    );
  });
});
