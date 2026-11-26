import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { aPerformance, aPerformanceDetail, aSeat } from '../test/data';
import { PERFORMANCE_DETAIL_PATH, performanceDetailHandler, PERFORMANCES_PATH } from '../test/handlers';
import { server } from '../test/server';
import { getPerformance, getPerformances } from './performances';

describe('공연 목록 API', () => {
  it('GET /api/performances로 공연 목록을 불러와 받은 순서대로 돌려준다', async () => {
    const performances = [aPerformance({ id: 2 }), aPerformance({ id: 1 })];
    server.use(http.get(PERFORMANCES_PATH, () => HttpResponse.json(performances)));

    await expect(getPerformances()).resolves.toEqual(performances);
  });

  it('R6: 공연 목록 요청에는 저장된 토큰이 있어도 Authorization 헤더를 붙이지 않는다', async () => {
    saveAccessToken('stale-token');
    let authorization: string | null = 'not-called';
    server.use(
      http.get(PERFORMANCES_PATH, ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json([]);
      }),
    );

    await getPerformances();

    expect(authorization).toBeNull();
  });
});

describe('공연 상세 API', () => {
  it('GET /api/performances/{id}로 공연 상세와 좌석(격자 위치 포함)을 불러온다', async () => {
    const detail = aPerformanceDetail({
      id: 7,
      seats: [aSeat({ id: 101, row: 'A', number: 5, gridRow: 1, gridColumn: 6, status: 'HELD' })],
    });
    server.use(performanceDetailHandler(detail));

    await expect(getPerformance('7')).resolves.toEqual(detail);
  });

  it('주소의 공연 번호를 경로 한 칸으로만 보낸다(/나 ?가 있어도 다른 경로나 쿼리가 되지 않는다)', async () => {
    let received: { performanceId: unknown; search: string } | undefined;
    server.use(
      http.get(PERFORMANCE_DETAIL_PATH, ({ params, request }) => {
        received = { performanceId: params.performanceId, search: new URL(request.url).search };
        return HttpResponse.json(aPerformanceDetail());
      }),
    );

    await getPerformance('1/2?x=3');

    expect(received).toEqual({ performanceId: '1/2?x=3', search: '' });
  });

  it('R6: 공연 상세 요청에는 저장된 토큰이 있어도 Authorization 헤더를 붙이지 않는다', async () => {
    saveAccessToken('stale-token');
    let authorization: string | null = 'not-called';
    server.use(
      http.get(PERFORMANCE_DETAIL_PATH, ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json(aPerformanceDetail());
      }),
    );

    await getPerformance('1');

    expect(authorization).toBeNull();
  });
});
