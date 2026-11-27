import { expect, type APIRequestContext } from '@playwright/test';
import type { Account } from './accounts';

/**
 * 테스트 준비와 '다른 사용자'의 행동에 쓰는 API 도우미. `request` 픽스처로 부른다.
 * 경로는 `/api`로 시작하는 상대 경로이고, 프론트엔드 개발 서버(baseURL)가 백엔드로 넘긴다(frontend/CLAUDE.md).
 */

/** 공연 상세의 좌석(spec 5 Seat). 좌석 목록은 row, number 오름차순이다. */
export interface Seat {
  id: number;
  row: string;
  number: number;
  status: 'AVAILABLE' | 'HELD' | 'SOLD';
}

/** 테스트가 고른 공연. index는 공연 목록 화면에서의 위치다(화면은 API가 준 순서대로 보여 준다, spec 7.7). */
export interface ChosenPerformance {
  index: number;
  id: number;
  title: string;
  /** 빈 좌석(AVAILABLE). 좌석 순서(row, number)다. */
  availableSeats: Seat[];
}

/** E2E 데이터 초기화(spec 6.9). 데이터를 비우고 시드를 다시 넣는다. 모든 테스트가 beforeEach에서 부른다(e2e/CLAUDE.md). */
export async function resetData(request: APIRequestContext): Promise<void> {
  const response = await request.post('/api/test-support/reset');
  expect(response.status(), 'POST /api/test-support/reset').toBe(204);
}

/**
 * 공연 목록·상세 API로 빈 좌석이 minAvailable석 이상인 첫 공연을 찾는다.
 * 초기화 뒤의 id나 시드 값(제목, 좌석 수)을 가정하지 않는다(spec 2.5, 6.9).
 */
export async function findPerformance(request: APIRequestContext, minAvailable: number): Promise<ChosenPerformance> {
  const performances = await getJson<{ id: number; title: string }[]>(request, '/api/performances');
  for (const [index, performance] of performances.entries()) {
    const detail = await getJson<{ seats: Seat[] }>(request, `/api/performances/${performance.id}`);
    const availableSeats = detail.seats.filter((seat) => seat.status === 'AVAILABLE');
    if (availableSeats.length >= minAvailable) {
      return { index, id: performance.id, title: performance.title, availableSeats };
    }
  }
  throw new Error(`빈 좌석이 ${minAvailable}석 이상인 공연이 없다(공연 ${performances.length}개)`);
}

/** 공연 상세 API로 본 좌석 하나의 상태 */
export async function seatStatus(request: APIRequestContext, performanceId: number, seatId: number): Promise<Seat['status']> {
  const detail = await getJson<{ seats: Seat[] }>(request, `/api/performances/${performanceId}`);
  const seat = detail.seats.find((candidate) => candidate.id === seatId);
  if (seat === undefined) {
    throw new Error(`공연 ${performanceId}에 좌석 ${seatId}가 없다`);
  }
  return seat.status;
}

/** account로 좌석 묶음을 선점한다(spec 6.4). 다른 사용자의 행동을 브라우저를 더 띄우지 않고 만든다(e2e/CLAUDE.md). */
export async function holdSeats(
  request: APIRequestContext,
  account: Account,
  performanceId: number,
  seatIds: number[],
): Promise<void> {
  const token = await logIn(request, account);
  const response = await request.post('/api/holds', {
    headers: { Authorization: `Bearer ${token}` },
    data: { performanceId, seatIds },
  });
  expect(response.status(), `${account.username}의 POST /api/holds`).toBe(201);
}

/** 로그인 API(spec 6.1)로 액세스 토큰을 받는다. */
async function logIn(request: APIRequestContext, account: Account): Promise<string> {
  const response = await request.post('/api/auth/login', { data: account });
  expect(response.status(), `${account.username}의 POST /api/auth/login`).toBe(200);
  const body = (await response.json()) as { accessToken: string };
  return body.accessToken;
}

async function getJson<T>(request: APIRequestContext, path: string): Promise<T> {
  const response = await request.get(path);
  expect(response.status(), `GET ${path}`).toBe(200);
  return (await response.json()) as T;
}
