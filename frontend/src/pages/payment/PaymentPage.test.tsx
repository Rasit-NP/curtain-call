import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { REQUEST_TIMEOUT_MS } from '../../api/client';
import type { Hold } from '../../api/types';
import { getAccessToken, saveAccessToken } from '../../auth/tokenStorage';
import { aHold, aPaymentResult, aPerformance, aPerformanceDetail, aReservation, aSeat, aSeatRef } from '../../test/data';
import {
  HOLD_PATH,
  holdCreatedHandler,
  holdHandler,
  holdReleaseHandler,
  PAYMENTS_PATH,
  paymentHandler,
  performanceDetailHandler,
  performancesHandler,
  problem,
  reservationsHandler,
  type PaymentRequestRecord,
} from '../../test/handlers';
import { renderApp } from '../../test/renderApp';
import { server } from '../../test/server';

const PAGE_PATH = '/holds/31';
const SEAT_PATH = '/performances/7';

const performance = aPerformance({
  id: 7,
  title: '한여름 밤의 꿈',
  venue: '혜화 소극장',
  startsAt: '2027-01-15T15:30:00Z',
  price: 30000,
});
const seatRefs = [aSeatRef({ id: 101, row: 'A', number: 1 }), aSeatRef({ id: 103, row: 'A', number: 3 })];

function hold(overrides: Partial<Hold> = {}): Hold {
  return aHold({
    id: 31,
    status: 'ACTIVE',
    performance,
    seats: seatRefs,
    amount: 60000,
    expiresAt: '2026-12-01T10:05:00Z',
    ...overrides,
  });
}

const approved = aPaymentResult({
  paymentId: 9,
  amount: 60000,
  reservation: aReservation({ id: 5, performance, seats: seatRefs, amount: 60000, reservedAt: '2026-12-01T10:03:00Z' }),
});

const LIMIT_NOTICE = '선택한 좌석은 5분 동안 잡아 둡니다. 5분 안에 결제해 주세요.';
const MESSAGE = {
  HOLD_EXPIRED: '좌석 선점 시간(5분)이 지났습니다. 좌석을 다시 선택해 주세요.',
  PAYMENT_FAILED: '결제가 승인되지 않았습니다. 다시 시도해 주세요.',
  NOT_FOUND: '요청한 정보를 찾을 수 없습니다.',
  INVALID_INPUT: '입력한 내용을 확인해 주세요.',
  INTERNAL_ERROR: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  NO_RESPONSE: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
};
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** 화면 본문(main). 공통 헤더의 메뉴와 섞이지 않게 여기서 찾는다. */
function page() {
  return within(screen.getByRole('main'));
}

function header() {
  return within(screen.getByRole('banner'));
}

function payButton() {
  return page().getByRole('button', { name: '결제하기' });
}

function backButton() {
  return page().getByRole('button', { name: '뒤로 가기' });
}

/** 응답을 테스트가 풀 때까지 붙잡아 두는 약속. */
function gate() {
  let open!: () => void;
  const opened = new Promise<void>((resolve) => (open = resolve));
  return { open, opened };
}

/** 결제 요청을 차례로 responses로 응답하고(마지막 응답을 되풀이) 받은 요청(멱등 키, 본문)을 쌓는다. */
function payments(...responses: Array<() => Response | Promise<Response>>) {
  const requests: PaymentRequestRecord[] = [];
  let count = 0;
  server.use(
    paymentHandler(() => {
      const respond = responses[Math.min(count, responses.length - 1)];
      count += 1;
      return respond();
    }, requests),
  );
  return requests;
}

/** 선점 해제 요청을 204로 응답하고 해제한 선점 번호를 쌓는다. 토큰 없이 오면 401이다. */
function releases() {
  const released: string[] = [];
  server.use(holdReleaseHandler(released));
  return released;
}

/** 토큰을 저장하고 결제 화면을 열어 선점 정보가 그려질 때까지 기다린다. */
async function openPage(
  entries: string | string[] = PAGE_PATH,
  current: Hold = hold(),
  options: Parameters<typeof renderApp>[1] = {},
) {
  saveAccessToken('token-1');
  server.use(holdHandler(current));
  const rendered = renderApp(entries, options);
  await page().findByRole('heading', { level: 2, name: current.performance.title });
  return rendered;
}

async function login(user: ReturnType<typeof renderApp>['user'], token = 'token-1') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json({ accessToken: token, expiresIn: 3600 })));
  // 라우터는 화면 이동을 transition으로 그려, 위치가 바뀐 뒤에도 잠시 이전 화면이 남아 있을 수 있다.
  await user.type(await screen.findByLabelText('아이디'), 'user1');
  await user.type(screen.getByLabelText('비밀번호'), 'password1');
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

/** 좌석 선택 화면에서 A1을 골라 선점하고 결제 화면으로 넘어온다(좌석 선택 화면의 201 뒤 이동). */
async function arriveFromSeatSelect() {
  saveAccessToken('token-1');
  server.use(
    performanceDetailHandler(
      aPerformanceDetail({ ...performance, seats: [aSeat({ id: 101, row: 'A', number: 1 })] }),
    ),
    holdCreatedHandler(hold()),
    holdHandler(hold()),
  );
  const rendered = renderApp(SEAT_PATH);
  await rendered.user.click(await page().findByRole('button', { name: 'A1 선택 가능' }));
  await rendered.user.click(page().getByRole('button', { name: '좌석 선택 완료' }));
  await page().findByRole('heading', { level: 1, name: '결제' });
  await page().findByRole('heading', { level: 2, name: performance.title });
  return rendered;
}

describe('결제 화면: 표시', () => {
  it('화면 제목 "결제" 아래에 공연 제목, 일시(한국 시간)·장소, 좌석, 금액, 제한 시간 안내와 "결제하기"·"뒤로 가기"를 보여 준다', async () => {
    await openPage();

    expect(page().getByRole('heading', { level: 1, name: '결제' })).toBeInTheDocument();
    expect(page().getByText('2027. 1. 16.(토) 00:30 · 혜화 소극장')).toBeInTheDocument();
    expect(page().getByText('좌석 A1, A3')).toBeInTheDocument();
    expect(page().getByText('금액 60,000원')).toBeInTheDocument();
    expect(page().getByText(LIMIT_NOTICE)).toBeInTheDocument();
    expect(payButton()).toBeEnabled();
    expect(backButton()).toBeEnabled();
    expect(page().queryByRole('alert')).not.toBeInTheDocument();
    // 남은 시간(시계)은 보여 주지 않는다(결정 0130).
    expect(page().queryByText(/남은 시간/)).not.toBeInTheDocument();
  });

  it('응답 전에는 화면 제목과 "불러오는 중입니다."를 role="status"로 보여 주고, 받으면 지운다', async () => {
    saveAccessToken('token-1');
    const { open, opened } = gate();
    server.use(
      http.get(HOLD_PATH, async () => {
        await opened;
        return HttpResponse.json(hold());
      }),
    );
    renderApp(PAGE_PATH);

    expect(page().getByRole('heading', { level: 1, name: '결제' })).toBeInTheDocument();
    expect(page().getByRole('status')).toHaveTextContent('불러오는 중입니다.');
    expect(page().queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();

    open();

    await page().findByRole('heading', { level: 2, name: performance.title });
    expect(page().queryByRole('status')).not.toBeInTheDocument();
  });

  it('선점이 404 NOT_FOUND면 안내 문구와 "내 예매" 링크를 보여 주고, 링크를 누르면 해제 요청을 보낸 뒤 내 예매로 간다', async () => {
    saveAccessToken('token-1');
    server.use(http.get(HOLD_PATH, () => problem(404, 'NOT_FOUND')), reservationsHandler([]));
    const released = releases();
    const { router, user } = renderApp(PAGE_PATH);

    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.NOT_FOUND);
    expect(page().queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();

    await user.click(page().getByRole('link', { name: '내 예매' }));

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(router.state.location.pathname).toBe('/reservations');
    // 결제가 승인되기 전에 떠나므로 화면을 불러오지 못했어도 해제를 보낸다(spec 7.3).
    expect(released).toEqual(['31']);
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), MESSAGE.INTERNAL_ERROR],
    ['400 INVALID_INPUT', () => problem(400, 'INVALID_INPUT'), MESSAGE.INVALID_INPUT],
    ['code 없는 오류 응답', () => new HttpResponse('Internal Server Error', { status: 500 }), MESSAGE.INTERNAL_ERROR],
    ['응답 없음', () => HttpResponse.error(), MESSAGE.NO_RESPONSE],
  ])('선점 조회가 %s면 code별 안내 문구만 보여 주고 결제 버튼과 링크는 보여 주지 않는다', async (_, respond, message) => {
    saveAccessToken('token-1');
    server.use(http.get(HOLD_PATH, respond));
    renderApp(PAGE_PATH);

    expect(await page().findByRole('alert')).toHaveTextContent(message);
    expect(page().queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
    expect(page().queryByRole('link')).not.toBeInTheDocument();
  });

  it('선점 조회가 AUTH_REQUIRED로 끝나도 결제 화면에는 "로그인이 필요합니다."를 그리지 않는다(다시 로그인한 뒤에 끝난 이전 요청)', async () => {
    saveAccessToken('expired-token');
    server.use(
      http.get(HOLD_PATH, () => {
        // 응답이 오기 전에 다른 곳에서 다시 로그인한 상황. 이전 토큰의 AUTH_REQUIRED라 공통 처리는 화면을 옮기지 않는다(결정 0043).
        saveAccessToken('new-token');
        return problem(401, 'AUTH_REQUIRED');
      }),
    );
    const { router } = renderApp(PAGE_PATH);

    await waitFor(() => expect(page().queryByRole('status')).not.toBeInTheDocument());
    expect(page().queryByRole('alert')).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
  });
});

describe('결제 화면: 만료(R3·R4)', () => {
  it('R3: 선점이 EXPIRED면 공연·좌석·금액과 만료 안내를 보여 주고, "결제하기"는 남기되 누를 수 없으며 "뒤로 가기"는 누를 수 있다', async () => {
    await openPage(PAGE_PATH, hold({ status: 'EXPIRED' }));

    expect(page().getByRole('alert')).toHaveTextContent(MESSAGE.HOLD_EXPIRED);
    expect(page().getByText('좌석 A1, A3')).toBeInTheDocument();
    expect(page().getByText('금액 60,000원')).toBeInTheDocument();
    expect(payButton()).toBeDisabled();
    expect(backButton()).toBeEnabled();
  });

  it('R3: 화면은 시간을 세지 않는다. 기기 시계로 만료 시각이 지나도 선점이 ACTIVE로 왔으면 결제 버튼을 막지 않는다', async () => {
    // 기기 시계는 서버 시계와 같다는 보장이 없어 만료는 서버만 판단한다(결정 0130).
    vi.useFakeTimers({
      toFake: ['Date', 'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
      shouldAdvanceTime: true,
    });
    vi.setSystemTime(new Date('2026-12-01T10:04:59Z'));
    await openPage(PAGE_PATH, hold({ expiresAt: '2026-12-01T10:05:00Z' }));

    await act(() => vi.advanceTimersByTimeAsync(60_000));

    expect(new Date().getTime()).toBeGreaterThan(Date.parse('2026-12-01T10:05:00Z'));
    expect(payButton()).toBeEnabled();
    expect(page().queryByRole('alert')).not.toBeInTheDocument();
  });

  it('R4: 결제 응답이 409 HOLD_EXPIRED면 만료 안내를 보여 주고, "결제하기"는 남기되 누를 수 없으며 "뒤로 가기"는 누를 수 있다', async () => {
    const { user } = await openPage();
    payments(() => problem(409, 'HOLD_EXPIRED'));

    await user.click(payButton());

    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.HOLD_EXPIRED);
    expect(payButton()).toBeDisabled();
    expect(backButton()).toBeEnabled();
  });
});

describe('결제 화면: 결제하기', () => {
  it('"결제하기"는 POST /api/payments(holdId)를 보내고, 201이면 제목을 "예매 완료"로 바꾸고 완료 문구, 공연, 좌석, 금액과 "내 예매" 링크를 보여 준다', async () => {
    const { user } = await openPage();
    const requests = payments(() => HttpResponse.json(approved, { status: 201 }));

    await user.click(payButton());

    expect(await page().findByRole('heading', { level: 1, name: '예매 완료' })).toBeInTheDocument();
    expect(page().getByRole('status')).toHaveTextContent('예매가 완료되었습니다.');
    expect(page().getByRole('heading', { level: 2, name: '한여름 밤의 꿈' })).toBeInTheDocument();
    expect(page().getByText('2027. 1. 16.(토) 00:30 · 혜화 소극장')).toBeInTheDocument();
    expect(page().getByText('좌석 A1, A3')).toBeInTheDocument();
    expect(page().getByText('금액 60,000원')).toBeInTheDocument();
    expect(page().getByRole('link', { name: '내 예매' })).toHaveAttribute('href', '/reservations');
    expect(page().queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
    expect(page().queryByRole('button', { name: '뒤로 가기' })).not.toBeInTheDocument();
    expect(page().queryByText(LIMIT_NOTICE)).not.toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].body).toEqual({ holdId: 31 });
  });

  it('402 PAYMENT_FAILED면 안내 문구를 보여 주고 다시 결제할 수 있으며, 다시 누르면 이전 문구를 지운다', async () => {
    const { user } = await openPage();
    const { open, opened } = gate();
    const requests = payments(
      () => problem(402, 'PAYMENT_FAILED'),
      async () => {
        await opened;
        return HttpResponse.json(approved, { status: 201 });
      },
    );

    await user.click(payButton());

    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.PAYMENT_FAILED);
    expect(payButton()).toBeEnabled();

    await user.click(payButton());

    await waitFor(() => expect(page().queryByRole('alert')).not.toBeInTheDocument());
    open();
    expect(await page().findByRole('heading', { level: 1, name: '예매 완료' })).toBeInTheDocument();
    expect(requests).toHaveLength(2);
  });

  it('404 NOT_FOUND면 안내 문구와 "내 예매" 링크를 보여 주고 "결제하기"를 지운다', async () => {
    const { user } = await openPage();
    payments(() => problem(404, 'NOT_FOUND'));

    await user.click(payButton());

    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.NOT_FOUND);
    expect(page().getByRole('link', { name: '내 예매' })).toHaveAttribute('href', '/reservations');
    expect(page().queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
    expect(backButton()).toBeEnabled();
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), MESSAGE.INTERNAL_ERROR],
    ['400 INVALID_INPUT', () => problem(400, 'INVALID_INPUT'), MESSAGE.INVALID_INPUT],
    ['code 없는 오류 응답', () => new HttpResponse('Internal Server Error', { status: 500 }), MESSAGE.INTERNAL_ERROR],
    ['응답 없음', () => HttpResponse.error(), MESSAGE.NO_RESPONSE],
  ])('%s면 code별 안내 문구를 보여 주고, 자동으로 다시 보내지 않으며 다시 결제할 수 있다', async (_, respond, message) => {
    const { user } = await openPage();
    const requests = payments(respond);

    await user.click(payButton());

    expect(await page().findByRole('alert')).toHaveTextContent(message);
    expect(payButton()).toBeEnabled();
    expect(requests).toHaveLength(1);
  });

  it('R5: 응답을 받았으면(402, 500, 400) 상태 코드와 상관없이 다음에 누를 때 새 멱등 키를 만든다', async () => {
    const { user } = await openPage();
    const requests = payments(
      () => problem(402, 'PAYMENT_FAILED'),
      () => problem(500, 'INTERNAL_ERROR'),
      () => problem(400, 'INVALID_INPUT'),
      () => HttpResponse.json(approved, { status: 201 }),
    );

    for (const message of [MESSAGE.PAYMENT_FAILED, MESSAGE.INTERNAL_ERROR, MESSAGE.INVALID_INPUT]) {
      await user.click(payButton());
      expect(await page().findByRole('alert')).toHaveTextContent(message);
    }
    await user.click(payButton());
    await page().findByRole('heading', { level: 1, name: '예매 완료' });

    const keys = requests.map((request) => request.idempotencyKey);
    expect(keys).toHaveLength(4);
    keys.forEach((key) => expect(key).toMatch(UUID));
    expect(new Set(keys).size).toBe(4);
  });

  it('R5: 응답을 받지 못했으면(네트워크 오류, code 없는 502) 다음에 누를 때 같은 멱등 키를 다시 쓰고, 응답을 받은 뒤에는 새 키를 만든다', async () => {
    const { user } = await openPage();
    const requests = payments(
      () => HttpResponse.error(),
      () => new HttpResponse(null, { status: 502 }),
      () => problem(402, 'PAYMENT_FAILED'),
      () => HttpResponse.json(approved, { status: 201 }),
    );

    for (const message of [MESSAGE.NO_RESPONSE, MESSAGE.NO_RESPONSE, MESSAGE.PAYMENT_FAILED]) {
      await user.click(payButton());
      expect(await page().findByRole('alert')).toHaveTextContent(message);
    }
    await user.click(payButton());
    await page().findByRole('heading', { level: 1, name: '예매 완료' });

    const [first, second, third, fourth] = requests.map((request) => request.idempotencyKey);
    expect(first).toMatch(UUID);
    expect(second).toBe(first);
    expect(third).toBe(first);
    expect(fourth).not.toBe(first);
    expect(fourth).toMatch(UUID);
  });

  it('R5: 결제 요청이 10초 동안 응답하지 않으면 응답 없음 문구를 보여 주고, 다음에 누를 때 같은 멱등 키를 다시 쓴다', async () => {
    // React Testing Library는 user-event 조작 뒤 setTimeout(0)을 기다리는데, vitest의 가짜 타이머는 알아보지 못한다.
    // shouldAdvanceTime으로 가짜 시계가 실제 시간만큼은 흐르게 해 그 대기를 풀고, 10초는 advanceTimersByTimeAsync로 앞당긴다.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true });
    const { user } = await openPage(PAGE_PATH, hold(), { advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
    const requests = payments(
      () => new Promise<never>(() => {}),
      () => problem(402, 'PAYMENT_FAILED'),
    );

    await user.click(payButton());
    await act(() => vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS));

    expect(page().getByRole('alert')).toHaveTextContent(MESSAGE.NO_RESPONSE);
    expect(payButton()).toBeEnabled();

    await user.click(payButton());

    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.PAYMENT_FAILED);
    expect(requests).toHaveLength(2);
    expect(requests[1].idempotencyKey).toBe(requests[0].idempotencyKey);
  });

  it('R5: 결제 요청 중에는 "결제하기"와 "뒤로 가기"를 누를 수 없고 "결제를 처리하는 중입니다."를 보여 주며, 두 번 눌러도 요청은 한 번만 간다', async () => {
    const { user } = await openPage();
    const { open, opened } = gate();
    const requests = payments(async () => {
      await opened;
      return problem(402, 'PAYMENT_FAILED');
    });

    await user.dblClick(payButton());

    await waitFor(() => expect(payButton()).toBeDisabled());
    expect(backButton()).toBeDisabled();
    expect(page().getByRole('status')).toHaveTextContent('결제를 처리하는 중입니다.');

    open();

    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.PAYMENT_FAILED);
    expect(page().queryByRole('status')).not.toBeInTheDocument();
    expect(payButton()).toBeEnabled();
    expect(requests).toHaveLength(1);
  });
});

describe('결제 화면: 떠날 때 선점 해제', () => {
  it('승인 전에 브라우저 뒤로가기로 떠나면 해제 요청을 보낸 뒤 좌석 선택 화면으로 돌아간다', async () => {
    server.use(performanceDetailHandler(aPerformanceDetail({ ...performance, seats: [aSeat()] })));
    const released = releases();
    const { router } = await openPage([SEAT_PATH, PAGE_PATH]);

    await act(() => router.navigate(-1));

    await page().findByRole('heading', { level: 1, name: '좌석 선택' });
    expect(router.state.location.pathname).toBe(SEAT_PATH);
    expect(released).toEqual(['31']);
  });

  it('좌석 선택 화면의 201 뒤 이동은 실제 결제 화면을 그리고, "뒤로 가기"를 누르면 해제 요청을 보낸 뒤 기록을 되돌려 좌석 선택 화면으로 간다', async () => {
    const { router, user } = await arriveFromSeatSelect();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
    const released = releases();

    await user.click(backButton());

    await page().findByRole('button', { name: 'A1 선택 가능' });
    expect(router.state.location.pathname).toBe(SEAT_PATH);
    expect(router.state.historyAction).toBe('POP');
    expect(released).toEqual(['31']);
  });

  it('주소로 바로 들어왔으면 "뒤로 가기"는 해제 요청을 보낸 뒤 지금 기록을 그 공연의 좌석 선택 화면으로 바꾼다', async () => {
    server.use(performanceDetailHandler(aPerformanceDetail({ ...performance, seats: [aSeat()] })));
    const released = releases();
    const { router, user } = await openPage();

    await user.click(backButton());

    await page().findByRole('heading', { level: 1, name: '좌석 선택' });
    expect(router.state.location.pathname).toBe(SEAT_PATH);
    expect(router.state.historyAction).toBe('REPLACE');
    expect(released).toEqual(['31']);
  });

  it.each([
    ['내 예매', '/reservations', '내 예매'],
    ['커튼콜', '/', '공연 목록'],
  ])('헤더의 "%s"로 떠나면 해제 요청을 보낸 뒤 이동한다', async (link, path, title) => {
    server.use(reservationsHandler([]), performancesHandler([]));
    const released = releases();
    const { router, user } = await openPage();

    await user.click(header().getByRole('link', { name: link }));

    await screen.findByRole('heading', { level: 1, name: title });
    expect(router.state.location.pathname).toBe(path);
    expect(released).toEqual(['31']);
  });

  it('로그아웃하면 토큰을 지우기 전에(토큰을 붙여) 해제 요청을 보내고, 토큰을 지운 뒤 공연 목록으로 간다', async () => {
    server.use(performancesHandler([]));
    // 토큰 없이 오면 401을 주고 쌓지 않으므로, 쌓였으면 토큰을 붙여 보낸 것이다.
    const released = releases();
    const { router, user } = await openPage();

    await user.click(header().getByRole('button', { name: '로그아웃' }));

    await screen.findByRole('heading', { level: 1, name: '공연 목록' });
    expect(router.state.location.pathname).toBe('/');
    expect(released).toEqual(['31']);
    expect(getAccessToken()).toBeNull();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('결제가 거절(402)된 뒤에 떠나도 해제 요청을 보낸다', async () => {
    server.use(reservationsHandler([]));
    const released = releases();
    const { router, user } = await openPage();
    payments(() => problem(402, 'PAYMENT_FAILED'));
    await user.click(payButton());
    await page().findByRole('alert');

    await user.click(header().getByRole('link', { name: '내 예매' }));

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(router.state.location.pathname).toBe('/reservations');
    expect(released).toEqual(['31']);
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR')],
    ['응답 없음', () => HttpResponse.error()],
  ])('해제 요청이 %s로 실패해도 이동한다', async (_, respond) => {
    server.use(reservationsHandler([]), http.delete(HOLD_PATH, respond));
    const { router, user } = await openPage();

    await user.click(header().getByRole('link', { name: '내 예매' }));

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(router.state.location.pathname).toBe('/reservations');
  });

  it('해제 응답을 받은 뒤에 이동하고, 그동안 "결제하기"를 누를 수 없다', async () => {
    server.use(reservationsHandler([]));
    const { open, opened } = gate();
    const released: string[] = [];
    server.use(
      http.delete(HOLD_PATH, async ({ params }) => {
        await opened;
        released.push(String(params.holdId));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router, user } = await openPage();

    await user.click(header().getByRole('link', { name: '내 예매' }));

    await waitFor(() => expect(payButton()).toBeDisabled());
    expect(router.state.location.pathname).toBe(PAGE_PATH);

    open();

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(router.state.location.pathname).toBe('/reservations');
    expect(released).toEqual(['31']);
  });

  it('결제가 승인된 뒤에는 떠나도 해제 요청을 보내지 않는다', async () => {
    server.use(reservationsHandler([]));
    const released = releases();
    const { router, user } = await openPage();
    payments(() => HttpResponse.json(approved, { status: 201 }));
    await user.click(payButton());
    await page().findByRole('heading', { level: 1, name: '예매 완료' });

    await user.click(page().getByRole('link', { name: '내 예매' }));

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(router.state.location.pathname).toBe('/reservations');
    expect(released).toEqual([]);
  });

  it('결제 요청 중(응답 전)에는 뒤로가기·헤더 메뉴·로그아웃으로 떠날 수 없고 해제 요청도 보내지 않으며, 응답을 받은 뒤에는 떠날 수 있다', async () => {
    server.use(reservationsHandler([]), performanceDetailHandler(aPerformanceDetail({ ...performance, seats: [aSeat()] })));
    const released = releases();
    const { open, opened } = gate();
    payments(async () => {
      await opened;
      return problem(402, 'PAYMENT_FAILED');
    });
    const { router, user } = await openPage([SEAT_PATH, PAGE_PATH]);
    await user.click(payButton());
    await page().findByRole('status');

    await user.click(header().getByRole('link', { name: '내 예매' }));
    await user.click(header().getByRole('link', { name: '커튼콜' }));
    await user.click(header().getByRole('button', { name: '로그아웃' }));
    await act(() => router.navigate(-1));

    expect(router.state.location.pathname).toBe(PAGE_PATH);
    expect(page().getByRole('heading', { level: 1, name: '결제' })).toBeInTheDocument();
    expect(getAccessToken()).toBe('token-1');
    expect(released).toEqual([]);

    open();
    expect(await page().findByRole('alert')).toHaveTextContent(MESSAGE.PAYMENT_FAILED);

    await user.click(header().getByRole('link', { name: '내 예매' }));

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(released).toEqual(['31']);
  });

  it('StrictMode로 그려도 들어오자마자 해제 요청을 보내지 않는다', async () => {
    const released = releases();

    await openPage(PAGE_PATH, hold(), { strict: true });
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));

    expect(released).toEqual([]);
    expect(payButton()).toBeEnabled();
  });
});

describe('결제 화면: 로그인(R6)', () => {
  it('R6: 로그인하지 않고 결제 화면에 들어가면 로그인 화면으로 보내고, 로그인하면 결제 화면으로 돌아온다(실제 경로)', async () => {
    server.use(holdHandler(hold()));
    const { router, user } = renderApp(PAGE_PATH);

    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('heading', { name: '결제' })).not.toBeInTheDocument();

    await login(user);

    expect(await page().findByRole('heading', { level: 2, name: performance.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
  });

  it('R6: 선점 조회·결제·해제 요청에 저장된 토큰을 Bearer로 붙인다', async () => {
    saveAccessToken('token-1');
    const authorizations: Record<string, string | null> = {};
    server.use(
      reservationsHandler([]),
      http.get(HOLD_PATH, ({ request }) => {
        authorizations.get = request.headers.get('Authorization');
        return HttpResponse.json(hold());
      }),
      http.post(PAYMENTS_PATH, ({ request }) => {
        authorizations.pay = request.headers.get('Authorization');
        return problem(402, 'PAYMENT_FAILED');
      }),
      http.delete(HOLD_PATH, ({ request }) => {
        authorizations.release = request.headers.get('Authorization');
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp(PAGE_PATH);
    await user.click(await page().findByRole('button', { name: '결제하기' }));
    await page().findByRole('alert');

    await user.click(header().getByRole('link', { name: '내 예매' }));

    await screen.findByRole('heading', { level: 1, name: '내 예매' });
    expect(authorizations).toEqual({ get: 'Bearer token-1', pay: 'Bearer token-1', release: 'Bearer token-1' });
  });

  it('R6: 결제 요청이 401 AUTH_REQUIRED면 해제 요청 없이 로그인 화면으로 가고, 다시 로그인하면 결제 화면으로 돌아와 같은 선점으로 결제할 수 있다', async () => {
    // 해제 핸들러를 두지 않는다. 해제를 보내면 '핸들러 없는 요청'으로 테스트가 실패한다.
    const requests = payments(
      () => problem(401, 'AUTH_REQUIRED'),
      () => HttpResponse.json(approved, { status: 201 }),
    );
    const { router, user } = await openPage();

    await user.click(payButton());

    expect(await screen.findByLabelText('아이디')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요합니다.');
    expect(getAccessToken()).toBeNull();

    await login(user, 'new-token');

    await user.click(await page().findByRole('button', { name: '결제하기' }));

    expect(await page().findByRole('heading', { level: 1, name: '예매 완료' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PAGE_PATH);
    expect(requests.map((request) => request.body)).toEqual([{ holdId: 31 }, { holdId: 31 }]);
  });
});
