import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { aPerformance } from '../test/data';
import { PERFORMANCES_PATH } from '../test/handlers';
import { server } from '../test/server';
import { getPerformances } from './performances';

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
