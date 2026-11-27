import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { PerformanceDetail, Seat, SeatStatus } from '../../api/types';
import { getAccessToken, saveAccessToken } from '../../auth/tokenStorage';
import { aHold, aPerformanceDetail, aSeat } from '../../test/data';
import {
  HOLDS_PATH,
  holdHandler as holdQueryHandler,
  holdReleaseHandler,
  PERFORMANCE_DETAIL_PATH,
  performanceDetailHandler,
  performancesHandler,
  problem,
  type HoldRequestBody,
} from '../../test/handlers';
import { renderApp } from '../../test/renderApp';
import { server } from '../../test/server';

const PAGE_PATH = '/performances/7';

/**
 * 2줄 6칸 배치. 3번째 칸이 통로라, 통로 오른쪽 좌석(A3~A5, B3~B5)은 표시 번호보다 한 칸 오른쪽(4~6번째 칸)에 있다.
 * 기본 상태: B3은 선점됨(HELD), B4는 판매됨(SOLD), 나머지는 선택 가능. id는 A1=101…A5=105, B1=201…B5=205.
 */
function layout(statuses: Record<string, SeatStatus> = {}): Seat[] {
  const defaults: Record<string, SeatStatus> = { B3: 'HELD', B4: 'SOLD' };
  return ['A', 'B'].flatMap((row, rowIndex) =>
    [1, 2, 3, 4, 5].map((number) => {
      const name = `${row}${number}`;
      return aSeat({
        id: (rowIndex + 1) * 100 + number,
        row,
        number,
        gridRow: rowIndex + 1,
        gridColumn: number <= 2 ? number : number + 1,
        status: statuses[name] ?? defaults[name] ?? 'AVAILABLE',
      });
    }),
  );
}

function detail(overrides: Partial<PerformanceDetail> = {}): PerformanceDetail {
  return aPerformanceDetail({
    id: 7,
    title: '한여름 밤의 꿈',
    venue: '혜화 소극장',
    startsAt: '2027-01-15T15:30:00Z',
    price: 30000,
    seats: layout(),
    ...overrides,
  });
}

/** 화면 본문(main). 공통 헤더의 메뉴와 섞이지 않게 여기서 찾는다. */
function page() {
  return within(screen.getByRole('main'));
}

function seat(name: string) {
  return page().getByRole('button', { name });
}

function submitButton() {
  return page().getByRole('button', { name: '좌석 선택 완료' });
}

function expectSummary(seats: string, total: string) {
  expect(page().getByText(/^선택한 좌석 /)).toHaveTextContent(`선택한 좌석 ${seats}`);
  expect(page().getByText(/^합계 /)).toHaveTextContent(`합계 ${total}`);
}

/** 응답을 테스트가 풀 때까지 붙잡아 두는 약속. */
function gate() {
  let open!: () => void;
  const opened = new Promise<void>((resolve) => (open = resolve));
  return { open, opened };
}

/** 토큰을 저장하고 좌석 선택 화면을 열어 좌석이 그려질 때까지 기다린다. */
async function openPage(performance: PerformanceDetail = detail()) {
  saveAccessToken('token-1');
  server.use(performanceDetailHandler(performance));
  const rendered = renderApp(PAGE_PATH);
  await page().findByRole('button', { name: 'A1 선택 가능' });
  return rendered;
}

/** POST /api/holds를 respond로 응답하고 받은 본문을 쌓는다. */
function holdHandler(respond: () => Response | Promise<Response>) {
  const requests: HoldRequestBody[] = [];
  server.use(
    http.post(HOLDS_PATH, async ({ request }) => {
      requests.push((await request.json()) as HoldRequestBody);
      return respond();
    }),
  );
  return requests;
}

/** 공연 상세 요청을 차례로 responses로 응답한다(마지막 응답을 되풀이). 받은 요청 수를 센다. */
function detailSequence(...responses: Array<() => Response | Promise<Response>>) {
  const calls = { count: 0 };
  server.use(
    http.get(PERFORMANCE_DETAIL_PATH, () => {
      const respond = responses[Math.min(calls.count, responses.length - 1)];
      calls.count += 1;
      return respond();
    }),
  );
  return calls;
}

async function login(user: ReturnType<typeof renderApp>['user'], token = 'token-1') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json({ accessToken: token, expiresIn: 3600 })));
  // 라우터는 화면 이동을 transition으로 그려, 위치가 바뀐 뒤에도 잠시 이전 화면이 남아 있을 수 있다.
  await user.type(await screen.findByLabelText('아이디'), 'user1');
  await user.type(screen.getByLabelText('비밀번호'), 'password1');
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

describe('좌석 선택 화면: 표시', () => {
  it('화면 제목 "좌석 선택" 아래에 공연 제목, 일시(한국 시간)·장소, 1석 가격을 보여 준다', async () => {
    await openPage();

    expect(page().getByRole('heading', { level: 1, name: '좌석 선택' })).toBeInTheDocument();
    expect(page().getByRole('heading', { level: 2, name: '한여름 밤의 꿈' })).toBeInTheDocument();
    expect(page().getByText('2027. 1. 16.(토) 00:30 · 혜화 소극장')).toBeInTheDocument();
    expect(page().getByText('1석 30,000원')).toBeInTheDocument();
  });

  it('좌석을 gridRow·gridColumn 위치에 그리고, 통로는 빈 칸으로 둔다(표시 번호와 칸이 다른 통로 오른쪽 좌석 포함)', async () => {
    await openPage();

    const grid = page().getByRole('group', { name: '좌석' });
    // 좌석 10석만 버튼이다. 통로(3번째 칸)에는 아무것도 그리지 않는다.
    expect(within(grid).getAllByRole('button')).toHaveLength(10);
    expect(seat('A2 선택 가능')).toHaveStyle({ gridRow: '1', gridColumn: '2' });
    // 통로 오른쪽 A3은 4번째 칸, A5는 6번째 칸이다.
    expect(seat('A3 선택 가능')).toHaveStyle({ gridRow: '1', gridColumn: '4' });
    expect(seat('A5 선택 가능')).toHaveStyle({ gridRow: '1', gridColumn: '6' });
    expect(seat('B1 선택 가능')).toHaveStyle({ gridRow: '2', gridColumn: '1' });
    // 격자 크기는 좌석 위치의 최댓값(2줄 6칸)이다. jsdom은 배치를 계산하지 않으므로, 명세 5장이 계약으로 정한
    // CSS Grid 값(좌석의 grid-row·grid-column, 격자의 줄·칸 수)을 그대로 확인한다. 칸 크기는 검사하지 않는다(결정 0082).
    expect(grid.style.gridTemplateRows).toMatch(/^repeat\(2, /);
    expect(grid.style.gridTemplateColumns).toMatch(/^repeat\(6, /);
  });

  it('맨 위 줄과 맨 왼쪽 칸이 비어 있어도 격자 크기는 좌석 위치의 최댓값이다(빈 줄·칸이 그대로 남는다)', async () => {
    await openPage(
      detail({
        seats: [
          aSeat({ id: 1, row: 'A', number: 1, gridRow: 1, gridColumn: 1 }),
          aSeat({ id: 2, row: 'C', number: 1, gridRow: 3, gridColumn: 3 }),
        ],
      }),
    );

    const grid = page().getByRole('group', { name: '좌석' });
    expect(grid.style.gridTemplateRows).toMatch(/^repeat\(3, /);
    expect(grid.style.gridTemplateColumns).toMatch(/^repeat\(3, /);
    expect(seat('C1 선택 가능')).toHaveStyle({ gridRow: '3', gridColumn: '3' });
  });

  it('격자 위(1번째 줄 쪽)에 "무대"를, 범례로 "선택 가능"·"선택 불가" 두 가지를 보여 준다', async () => {
    await openPage();

    const stage = page().getByText('무대');
    const grid = page().getByRole('group', { name: '좌석' });
    expect(stage.compareDocumentPosition(grid) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const legend = page().getByRole('list', { name: '좌석 상태' });
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['선택 가능', '선택 불가']);
  });

  it('좌석 버튼은 "A1"처럼 표시 이름을 보여 주고, 누르면 "선택 가능"과 "선택됨"이 바뀐다', async () => {
    const { user } = await openPage();

    expect(seat('A1 선택 가능')).toHaveTextContent('A1');

    await user.click(seat('A1 선택 가능'));
    expect(seat('A1 선택됨')).toBeEnabled();

    await user.click(seat('A1 선택됨'));
    expect(seat('A1 선택 가능')).toBeEnabled();
  });

  it('R1: 선점됨·판매됨 좌석은 "선택 불가"로 보이고 누를 수 없다', async () => {
    const { user } = await openPage();

    // B3은 선점됨(HELD), B4는 판매됨(SOLD)이다. 둘은 같은 이름과 모양으로 보인다.
    expect(seat('B3 선택 불가')).toBeDisabled();
    expect(seat('B4 선택 불가')).toBeDisabled();

    await user.click(seat('B3 선택 불가'));
    await user.click(seat('B4 선택 불가'));

    expect(seat('B3 선택 불가')).toBeDisabled();
    expectSummary('없음', '0원');
    expect(submitButton()).toBeDisabled();
  });

  it('고른 좌석은 좌석 순서로, 합계는 1석 가격 × 좌석 수로 보여 주고, 아무것도 고르지 않으면 "없음"과 0원이다', async () => {
    const { user } = await openPage();

    expectSummary('없음', '0원');
    expect(submitButton()).toBeDisabled();

    // 고른 순서(A3, B1, A1)와 상관없이 좌석 순서(A1, A3, B1)로 보여 준다.
    await user.click(seat('A3 선택 가능'));
    await user.click(seat('B1 선택 가능'));
    await user.click(seat('A1 선택 가능'));

    expectSummary('A1, A3, B1', '90,000원');
    expect(submitButton()).toBeEnabled();

    await user.click(seat('A3 선택됨'));

    expectSummary('A1, B1', '60,000원');
  });

  it('R2: 4석까지는 고를 수 있고, 4석을 고른 뒤 5번째 좌석은 선택되지 않고 안내 문구가 보인다', async () => {
    const { user } = await openPage();

    for (const name of ['A1', 'A2', 'A3', 'A4']) {
      await user.click(seat(`${name} 선택 가능`));
    }
    expectSummary('A1, A2, A3, A4', '120,000원');
    expect(page().queryByRole('alert')).not.toBeInTheDocument();

    await user.click(seat('A5 선택 가능'));

    expect(page().getByRole('alert')).toHaveTextContent('한 공연에서 최대 4석까지 예매할 수 있습니다.');
    expect(seat('A5 선택 가능')).toBeEnabled();
    expectSummary('A1, A2, A3, A4', '120,000원');
  });

  it('R2: 5번째 좌석을 눌러 뜬 안내 문구는 좌석을 취소하면 지우고, 5번째 좌석을 다시 누르면 다시 보인다', async () => {
    const { user } = await openPage();
    for (const name of ['A1', 'A2', 'A3', 'A4']) {
      await user.click(seat(`${name} 선택 가능`));
    }
    await user.click(seat('A5 선택 가능'));
    expect(page().getByRole('alert')).toBeInTheDocument();

    await user.click(seat('A4 선택됨'));
    expect(page().queryByRole('alert')).not.toBeInTheDocument();

    await user.click(seat('A5 선택 가능'));
    await user.click(seat('B1 선택 가능'));
    expect(page().getByRole('alert')).toHaveTextContent('한 공연에서 최대 4석까지 예매할 수 있습니다.');
    expectSummary('A1, A2, A3, A5', '120,000원');
  });
});

describe('좌석 선택 화면: 불러오기', () => {
  it('응답을 받기 전에는 화면 제목과 "불러오는 중입니다."를 보여 주고, 받으면 지운다', async () => {
    saveAccessToken('token-1');
    const { open, opened } = gate();
    server.use(
      http.get(PERFORMANCE_DETAIL_PATH, async () => {
        await opened;
        return HttpResponse.json(detail());
      }),
    );
    renderApp(PAGE_PATH);

    expect(await page().findByRole('status')).toHaveTextContent('불러오는 중입니다.');
    expect(page().getByRole('heading', { level: 1, name: '좌석 선택' })).toBeInTheDocument();
    expect(page().queryByRole('group', { name: '좌석' })).not.toBeInTheDocument();

    open();

    expect(await page().findByRole('button', { name: 'A1 선택 가능' })).toBeInTheDocument();
    expect(screen.queryByText('불러오는 중입니다.')).not.toBeInTheDocument();
  });

  it('공연이 없으면(404 NOT_FOUND) 안내 문구와 "공연 목록" 링크를 보여 주고, 링크를 누르면 공연 목록으로 간다', async () => {
    saveAccessToken('token-1');
    server.use(http.get(PERFORMANCE_DETAIL_PATH, () => problem(404, 'NOT_FOUND')), performancesHandler([]));
    const { router, user } = renderApp(PAGE_PATH);

    expect(await page().findByRole('alert')).toHaveTextContent('요청한 정보를 찾을 수 없습니다.');
    expect(page().queryByRole('group', { name: '좌석' })).not.toBeInTheDocument();
    const link = page().getByRole('link', { name: '공연 목록' });
    expect(link).toHaveAttribute('href', '/');

    await user.click(link);

    expect(await screen.findByRole('heading', { level: 1, name: '공연 목록' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['400 INVALID_INPUT', () => problem(400, 'INVALID_INPUT'), '입력한 내용을 확인해 주세요.'],
    ['code 없는 오류 응답', () => new HttpResponse('Internal Server Error', { status: 500 }), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['응답 없음(네트워크 오류)', () => HttpResponse.error(), '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
  ])('%s면 code별 안내 문구만 보여 주고 좌석과 "공연 목록" 링크는 보여 주지 않는다', async (_name, respond, message) => {
    saveAccessToken('token-1');
    server.use(http.get(PERFORMANCE_DETAIL_PATH, respond));

    renderApp(PAGE_PATH);

    expect(await page().findByRole('alert')).toHaveTextContent(message);
    expect(page().queryByRole('status')).not.toBeInTheDocument();
    expect(page().queryByRole('group', { name: '좌석' })).not.toBeInTheDocument();
    expect(page().queryByRole('link', { name: '공연 목록' })).not.toBeInTheDocument();
  });

  it('좌석이 하나도 없으면 공연 정보와 "좌석 정보가 없습니다."만 보여 주고 무대·범례·합계·완료 버튼은 보여 주지 않는다', async () => {
    saveAccessToken('token-1');
    server.use(performanceDetailHandler(detail({ seats: [] })));

    renderApp(PAGE_PATH);

    await waitFor(() => expect(page().getByRole('status')).toHaveTextContent('좌석 정보가 없습니다.'));
    expect(page().getByRole('heading', { level: 2, name: '한여름 밤의 꿈' })).toBeInTheDocument();
    expect(page().queryByText('무대')).not.toBeInTheDocument();
    expect(page().queryByRole('list', { name: '좌석 상태' })).not.toBeInTheDocument();
    expect(page().queryByText(/^합계 /)).not.toBeInTheDocument();
    expect(page().queryByRole('button', { name: '좌석 선택 완료' })).not.toBeInTheDocument();
    expect(page().queryByRole('alert')).not.toBeInTheDocument();
  });

  it('R1: 모든 좌석이 팔렸으면 빈 상태가 아니라 "선택 불가" 좌석으로 격자를 보여 준다', async () => {
    saveAccessToken('token-1');
    server.use(performanceDetailHandler(detail({ seats: layout({ A1: 'SOLD', A2: 'SOLD', A3: 'SOLD', A4: 'SOLD', A5: 'SOLD', B1: 'SOLD', B2: 'SOLD', B5: 'SOLD' }) })));

    renderApp(PAGE_PATH);

    expect(await page().findByRole('button', { name: 'A1 선택 불가' })).toBeDisabled();
    expect(page().queryByText('좌석 정보가 없습니다.')).not.toBeInTheDocument();
  });
});

describe('좌석 선택 화면: 좌석 선택 완료', () => {
  it('고른 좌석으로 POST /api/holds(performanceId, seatIds)를 보내고, 201이면 /holds/{id}로 일반 이동(push)한다', async () => {
    // 201 뒤에는 실제 결제 화면이 그려져 선점을 불러온다.
    server.use(holdQueryHandler(aHold({ id: 31 })));
    const { router, user } = await openPage();
    const requests = holdHandler(() => HttpResponse.json(aHold({ id: 31 }), { status: 201 }));

    await user.click(seat('A3 선택 가능'));
    await user.click(seat('A1 선택 가능'));
    await user.click(submitButton());

    await waitFor(() => expect(router.state.location.pathname).toBe('/holds/31'));
    expect(router.state.historyAction).toBe('PUSH');
    expect(requests).toEqual([{ performanceId: 7, seatIds: [101, 103] }]);
  });

  it('선점 요청 중에는 "좌석 선택 완료"와 좌석 버튼을 누를 수 없고, 두 번 눌러도 요청은 한 번만 간다', async () => {
    // 201 뒤에는 실제 결제 화면이 그려져 선점을 불러온다.
    server.use(holdQueryHandler(aHold({ id: 31 })));
    const { router, user } = await openPage();
    const { open, opened } = gate();
    const requests = holdHandler(async () => {
      await opened;
      return HttpResponse.json(aHold({ id: 31 }), { status: 201 });
    });
    await user.click(seat('A1 선택 가능'));

    await user.dblClick(submitButton());

    await waitFor(() => expect(submitButton()).toBeDisabled());
    expect(seat('A1 선택됨')).toBeDisabled();
    expect(seat('A2 선택 가능')).toBeDisabled();
    await user.click(seat('A2 선택 가능'));
    expectSummary('A1', '30,000원');

    open();

    await waitFor(() => expect(router.state.location.pathname).toBe('/holds/31'));
    expect(requests).toHaveLength(1);
  });

  it('"좌석 선택 완료"를 누르면 이전 안내 문구를 지운다', async () => {
    // 201 뒤에는 실제 결제 화면이 그려져 선점을 불러온다.
    server.use(holdQueryHandler(aHold({ id: 31 })));
    const { router, user } = await openPage();
    const { open, opened } = gate();
    holdHandler(async () => {
      await opened;
      return HttpResponse.json(aHold(), { status: 201 });
    });
    for (const name of ['A1', 'A2', 'A3', 'A4', 'A5']) {
      await user.click(seat(`${name} 선택 가능`));
    }
    expect(page().getByRole('alert')).toBeInTheDocument();

    await user.click(submitButton());

    await waitFor(() => expect(page().queryByRole('alert')).not.toBeInTheDocument());

    open();

    await waitFor(() => expect(router.state.location.pathname).toBe('/holds/31'));
  });

  it('R1: 선점 요청이 409 SEAT_ALREADY_HELD면 안내 문구를 보여 주고, 고른 좌석을 비운 뒤 좌석 상태를 다시 불러와 그 좌석을 "선택 불가"로 보여 준다', async () => {
    saveAccessToken('token-1');
    // 처음에는 A3이 선택 가능, 다시 불러오면 다른 사람이 잡아 선점됨이다.
    const calls = detailSequence(
      () => HttpResponse.json(detail()),
      () => HttpResponse.json(detail({ seats: layout({ A3: 'HELD' }) })),
    );
    const requests = holdHandler(() => problem(409, 'SEAT_ALREADY_HELD'));
    const { router, user } = renderApp(PAGE_PATH);
    await page().findByRole('button', { name: 'A1 선택 가능' });

    await user.click(seat('A1 선택 가능'));
    await user.click(seat('A3 선택 가능'));
    await user.click(submitButton());

    expect(await page().findByRole('button', { name: 'A3 선택 불가' })).toBeDisabled();
    expect(page().getByRole('alert')).toHaveTextContent('이미 다른 고객이 선택한 좌석입니다. 다른 좌석을 선택해 주세요.');
    expect(calls.count).toBe(2);
    expectSummary('없음', '0원');
    await waitFor(() => expect(seat('A1 선택 가능')).toBeEnabled());
    expect(submitButton()).toBeDisabled();
    expect(requests).toHaveLength(1);
    expect(router.state.location.pathname).toBe(PAGE_PATH);
  });

  it('R1: 409 SEAT_ALREADY_HELD 뒤 다시 불러오는 동안 격자와 안내 문구를 그대로 두고 로딩 문구는 보여 주지 않으며, 다시 불러오기가 실패하면 실패 문구를 함께 보여 준다', async () => {
    saveAccessToken('token-1');
    const { open, opened } = gate();
    detailSequence(
      () => HttpResponse.json(detail()),
      async () => {
        await opened;
        return HttpResponse.error();
      },
    );
    holdHandler(() => problem(409, 'SEAT_ALREADY_HELD'));
    const { user } = renderApp(PAGE_PATH);
    await page().findByRole('button', { name: 'A1 선택 가능' });
    await user.click(seat('A1 선택 가능'));

    await user.click(submitButton());

    expect(await page().findByRole('alert')).toHaveTextContent('이미 다른 고객이 선택한 좌석입니다.');
    expect(page().getByRole('group', { name: '좌석' })).toBeInTheDocument();
    expect(page().queryByRole('status')).not.toBeInTheDocument();
    // 다시 불러오는 동안에는 좌석을 고를 수 없다.
    expect(seat('A1 선택 가능')).toBeDisabled();

    open();

    await waitFor(() =>
      expect(page().getByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'),
    );
    expect(page().getByRole('alert')).toHaveTextContent('이미 다른 고객이 선택한 좌석입니다. 다른 좌석을 선택해 주세요.');
    expect(page().getByRole('group', { name: '좌석' })).toBeInTheDocument();
    expect(seat('A1 선택 가능')).toBeEnabled();
    expectSummary('없음', '0원');
  });

  it('R2: 선점 요청이 409 HOLD_LIMIT_EXCEEDED면 안내 문구를 보여 주고 고른 좌석은 그대로 두며 좌석 상태를 다시 불러오지 않는다', async () => {
    saveAccessToken('token-1');
    const calls = detailSequence(() => HttpResponse.json(detail()));
    holdHandler(() => problem(409, 'HOLD_LIMIT_EXCEEDED'));
    const { user } = renderApp(PAGE_PATH);
    await page().findByRole('button', { name: 'A1 선택 가능' });
    await user.click(seat('A1 선택 가능'));
    await user.click(seat('A2 선택 가능'));

    await user.click(submitButton());

    expect(await page().findByRole('alert')).toHaveTextContent('한 공연에서 최대 4석까지 예매할 수 있습니다.');
    expectSummary('A1, A2', '60,000원');
    await waitFor(() => expect(seat('A1 선택됨')).toBeEnabled());
    expect(submitButton()).toBeEnabled();
    expect(calls.count).toBe(1);
  });

  it('R2: 선점 요청이 409 HOLD_LIMIT_EXCEEDED로 끝나 뜬 안내 문구는 좌석을 하나 더 고르면 지운다', async () => {
    const { user } = await openPage();
    holdHandler(() => problem(409, 'HOLD_LIMIT_EXCEEDED'));
    await user.click(seat('A1 선택 가능'));
    await user.click(seat('A2 선택 가능'));
    await user.click(submitButton());
    expect(await page().findByRole('alert')).toHaveTextContent('한 공연에서 최대 4석까지 예매할 수 있습니다.');
    await waitFor(() => expect(seat('A3 선택 가능')).toBeEnabled());

    await user.click(seat('A3 선택 가능'));

    expect(page().queryByRole('alert')).not.toBeInTheDocument();
    expectSummary('A1, A2, A3', '90,000원');
  });

  it('선점 요청이 404 NOT_FOUND면 안내 문구와 "공연 목록" 링크를 보여 주고 고른 좌석은 그대로 둔다', async () => {
    const { user } = await openPage();
    holdHandler(() => problem(404, 'NOT_FOUND'));
    await user.click(seat('A1 선택 가능'));

    await user.click(submitButton());

    expect(await page().findByRole('alert')).toHaveTextContent('요청한 정보를 찾을 수 없습니다.');
    expect(page().getByRole('link', { name: '공연 목록' })).toHaveAttribute('href', '/');
    expectSummary('A1', '30,000원');
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['400 INVALID_INPUT', () => problem(400, 'INVALID_INPUT'), '입력한 내용을 확인해 주세요.'],
    ['code 없는 오류 응답', () => new HttpResponse('Internal Server Error', { status: 500 }), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['응답 없음(네트워크 오류)', () => HttpResponse.error(), '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
  ])('선점 요청이 %s면 code별 안내 문구를 보여 주고, 고른 좌석을 그대로 두며 자동으로 다시 보내지 않는다', async (_name, respond, message) => {
    const { user } = await openPage();
    const requests = holdHandler(respond);
    await user.click(seat('A1 선택 가능'));

    await user.click(submitButton());

    expect(await page().findByRole('alert')).toHaveTextContent(message);
    expectSummary('A1', '30,000원');
    await waitFor(() => expect(submitButton()).toBeEnabled());
    expect(page().queryByRole('link', { name: '공연 목록' })).not.toBeInTheDocument();
    expect(requests).toHaveLength(1);
  });
});

describe('좌석 선택 화면: 로그인(R6)', () => {
  it('R6: 로그인하지 않고 좌석 선택 화면에 들어가면 로그인 화면으로 보내고, 로그인하면 좌석 선택 화면으로 돌아온다', async () => {
    server.use(performanceDetailHandler(detail()));
    const { router, user } = renderApp(PAGE_PATH);

    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('heading', { name: '좌석 선택' })).not.toBeInTheDocument();

    await login(user);

    expect(await page().findByRole('button', { name: 'A1 선택 가능' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
  });

  it('R6: 저장된 토큰이 있어도 공연 상세 요청에는 토큰을 붙이지 않고, 선점 요청에는 Bearer로 붙인다', async () => {
    // 201 뒤에는 실제 결제 화면이 그려져 선점을 불러온다.
    server.use(holdQueryHandler(aHold({ id: 31 })));
    saveAccessToken('token-1');
    const authorizations: Record<string, string | null> = {};
    server.use(
      http.get(PERFORMANCE_DETAIL_PATH, ({ request }) => {
        authorizations.detail = request.headers.get('Authorization');
        return HttpResponse.json(detail());
      }),
      http.post(HOLDS_PATH, ({ request }) => {
        authorizations.hold = request.headers.get('Authorization');
        return HttpResponse.json(aHold({ id: 31 }), { status: 201 });
      }),
    );
    const { router, user } = renderApp(PAGE_PATH);
    await page().findByRole('button', { name: 'A1 선택 가능' });

    await user.click(seat('A1 선택 가능'));
    await user.click(submitButton());

    await waitFor(() => expect(router.state.location.pathname).toBe('/holds/31'));
    expect(authorizations).toEqual({ detail: null, hold: 'Bearer token-1' });
  });

  it('R6: 선점 요청이 401 AUTH_REQUIRED면 화면이 따로 처리하지 않아도 공통 처리로 로그인 화면에 가고, 로그인하면 좌석 선택 화면으로 돌아온다', async () => {
    saveAccessToken('expired-token');
    server.use(
      performanceDetailHandler(detail()),
      http.post(HOLDS_PATH, () => problem(401, 'AUTH_REQUIRED')),
    );
    const { router, user } = renderApp(PAGE_PATH);
    await page().findByRole('button', { name: 'A1 선택 가능' });
    await user.click(seat('A1 선택 가능'));

    await user.click(submitButton());

    // 좌석 선택 화면도 받은 오류의 문구를 잠시 보여 줄 수 있으므로, 로그인 화면이 그려진 뒤에 문구를 확인한다.
    expect(await screen.findByLabelText('아이디')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요합니다.');
    expect(getAccessToken()).toBeNull();

    await login(user, 'new-token');

    expect(await page().findByRole('button', { name: 'A1 선택 가능' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
  });
});

describe('좌석 선택 화면: 선점 요청 중에 떠나기', () => {
  function header() {
    return within(screen.getByRole('banner'));
  }

  it('선점 요청 중에 화면을 떠났는데 나중에 201이 오면 결제 화면으로 가지 않고 그 선점을 해제한다', async () => {
    const { router, user } = await openPage();
    const released: string[] = [];
    server.use(performancesHandler([]), holdReleaseHandler(released));
    const { open, opened } = gate();
    holdHandler(async () => {
      await opened;
      return HttpResponse.json(aHold({ id: 31 }), { status: 201 });
    });
    await user.click(seat('A1 선택 가능'));
    await user.click(submitButton());

    await user.click(header().getByRole('link', { name: '커튼콜' }));
    await screen.findByRole('heading', { level: 1, name: '공연 목록' });
    open();

    await waitFor(() => expect(released).toEqual(['31']));
    expect(router.state.location.pathname).toBe('/');
  });

  it('선점 요청 중에 로그아웃했으면 늦게 온 201의 선점은 해제 요청을 보내지 않는다(토큰이 없음)', async () => {
    const { router, user } = await openPage();
    // 해제 핸들러를 두지 않는다. 해제를 보내면 '핸들러 없는 요청'으로 테스트가 실패한다.
    server.use(performancesHandler([]));
    const { open, opened } = gate();
    const requests = holdHandler(async () => {
      await opened;
      return HttpResponse.json(aHold({ id: 31 }), { status: 201 });
    });
    await user.click(seat('A1 선택 가능'));
    await user.click(submitButton());
    await waitFor(() => expect(requests).toHaveLength(1));

    await user.click(header().getByRole('button', { name: '로그아웃' }));
    await screen.findByRole('heading', { level: 1, name: '공연 목록' });
    open();
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));

    expect(router.state.location.pathname).toBe('/');
    expect(getAccessToken()).toBeNull();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('선점 요청이 AUTH_REQUIRED로 끝나도 좌석 선택 화면에는 "로그인이 필요합니다."를 그리지 않는다(다시 로그인한 뒤에 끝난 이전 요청)', async () => {
    const { router, user } = await openPage();
    holdHandler(() => {
      // 응답이 오기 전에 다른 곳에서 다시 로그인한 상황. 이전 토큰의 AUTH_REQUIRED라 공통 처리는 화면을 옮기지 않는다(결정 0043).
      saveAccessToken('new-token');
      return problem(401, 'AUTH_REQUIRED');
    });
    await user.click(seat('A1 선택 가능'));

    await user.click(submitButton());

    await waitFor(() => expect(submitButton()).toBeEnabled());
    expect(page().queryByRole('alert')).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
  });
});
