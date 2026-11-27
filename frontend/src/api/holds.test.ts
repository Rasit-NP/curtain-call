import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { clearAccessToken, saveAccessToken } from '../auth/tokenStorage';
import { aHold } from '../test/data';
import { holdCreatedHandler, holdHandler, holdReleaseHandler, problem, type HoldRequestBody } from '../test/handlers';
import { server } from '../test/server';
import { ApiError } from './errors';
import { createHold, getHold, releaseHold, releaseHoldOnLeave } from './holds';

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

describe('선점 조회 API', () => {
  it('GET /api/holds/{holdId}로 내 선점을 불러온다', async () => {
    saveAccessToken('token-1');
    const hold = aHold({ id: 31, status: 'EXPIRED' });
    server.use(holdHandler(hold));

    await expect(getHold('31')).resolves.toEqual(hold);
  });

  it('R6: 선점 조회 요청에는 저장된 토큰을 Bearer로 붙인다', async () => {
    saveAccessToken('token-1');
    let authorization: string | null = 'not-called';
    server.use(
      http.get('/api/holds/:holdId', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json(aHold());
      }),
    );

    await getHold('31');

    expect(authorization).toBe('Bearer token-1');
  });

  it('선점 번호는 경로 한 칸으로만 들어가도록 인코딩한다', async () => {
    saveAccessToken('token-1');
    let holdId: unknown = 'not-called';
    server.use(
      http.get('/api/holds/:holdId', ({ params }) => {
        holdId = params.holdId;
        return problem(400, 'INVALID_INPUT');
      }),
    );

    await expect(getHold('1/2?x')).rejects.toEqual(new ApiError('INVALID_INPUT', 400));
    expect(holdId).toBe('1/2?x');
  });
});

describe('선점 해제 API', () => {
  it('DELETE /api/holds/{holdId}로 선점을 해제한다', async () => {
    saveAccessToken('token-1');
    const released: string[] = [];
    server.use(holdReleaseHandler(released));

    await expect(releaseHold(31)).resolves.toBeUndefined();
    expect(released).toEqual(['31']);
  });

  it('R6: 선점 해제 요청에는 저장된 토큰을 Bearer로 붙인다', async () => {
    saveAccessToken('token-1');
    let authorization: string | null = 'not-called';
    server.use(
      http.delete('/api/holds/:holdId', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await releaseHold(31);

    expect(authorization).toBe('Bearer token-1');
  });

  it('오류 응답이면 code를 담은 ApiError로 넘긴다', async () => {
    saveAccessToken('token-1');
    server.use(http.delete('/api/holds/:holdId', () => problem(404, 'NOT_FOUND')));

    await expect(releaseHold(31)).rejects.toEqual(new ApiError('NOT_FOUND', 404));
  });
});

describe('화면을 떠날 때의 선점 해제', () => {
  it('저장된 토큰이 있으면 해제를 보낸다', async () => {
    saveAccessToken('token-1');
    const released: string[] = [];
    server.use(holdReleaseHandler(released));

    await releaseHoldOnLeave(31);

    expect(released).toEqual(['31']);
  });

  it('해제가 실패해도(오류 응답, 응답 없음) 실패를 넘기지 않는다', async () => {
    saveAccessToken('token-1');
    server.use(http.delete('/api/holds/:holdId', () => problem(500, 'INTERNAL_ERROR')));
    await expect(releaseHoldOnLeave(31)).resolves.toBeUndefined();

    server.use(http.delete('/api/holds/:holdId', () => HttpResponse.error()));
    await expect(releaseHoldOnLeave(31)).resolves.toBeUndefined();
  });

  it('저장된 토큰이 없으면(로그아웃, AUTH_REQUIRED) 보내지 않는다', async () => {
    clearAccessToken();
    // 핸들러를 두지 않는다. 요청을 보내면 '핸들러 없는 요청'으로 테스트가 실패한다.

    await expect(releaseHoldOnLeave(31)).resolves.toBeUndefined();
  });
});
