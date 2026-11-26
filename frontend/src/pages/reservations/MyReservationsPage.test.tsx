import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { getAccessToken, saveAccessToken } from '../../auth/tokenStorage';
import { aPerformance, aReservation, aSeatRef } from '../../test/data';
import { performancesHandler, problem, RESERVATIONS_PATH, reservationsHandler } from '../../test/handlers';
import { renderApp } from '../../test/renderApp';
import { server } from '../../test/server';

/** 화면 본문(main). 공통 헤더의 메뉴와 섞이지 않게 여기서 찾는다. */
function page() {
  return within(screen.getByRole('main'));
}

async function login(user: ReturnType<typeof renderApp>['user'], token = 'token-1') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json({ accessToken: token, expiresIn: 3600 })));
  // 라우터는 화면 이동을 transition으로 그려, 위치가 바뀐 뒤에도 잠시 이전 화면이 남아 있을 수 있다.
  await user.type(await screen.findByLabelText('아이디'), 'user1');
  await user.type(screen.getByLabelText('비밀번호'), 'password1');
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

describe('내 예매 화면', () => {
  it('R6: 로그인하지 않고 내 예매 화면에 들어가면 로그인 화면으로 보내고, 로그인하면 내 예매 화면으로 돌아온다', async () => {
    server.use(reservationsHandler([aReservation({ performance: aPerformance({ title: '작은 방의 햄릿' }) })]));
    const { router, user } = renderApp('/reservations');

    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('heading', { name: '내 예매' })).not.toBeInTheDocument();

    await login(user);

    expect(await screen.findByRole('heading', { level: 1, name: '내 예매' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/reservations');
    expect(await screen.findByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
  });

  it('예매마다 공연 제목, 일시(한국 시간)·장소, 좌석, 금액을 API가 준 순서(최신순)대로 보여 준다', async () => {
    saveAccessToken('token-1');
    server.use(
      reservationsHandler([
        aReservation({
          id: 9,
          performance: aPerformance({ title: '한여름 밤의 꿈', venue: '혜화 소극장', startsAt: '2027-01-15T15:30:00Z' }),
          seats: [aSeatRef({ id: 201, row: 'B', number: 3 })],
          amount: 45000,
          reservedAt: '2026-12-02T00:00:00Z',
        }),
        aReservation({
          id: 4,
          performance: aPerformance({ title: '작은 방의 햄릿', venue: '대학로 커튼홀', startsAt: '2026-12-20T10:00:00Z' }),
          seats: [aSeatRef({ id: 101, row: 'A', number: 1 }), aSeatRef({ id: 102, row: 'A', number: 2 })],
          amount: 60000,
          reservedAt: '2026-12-01T00:00:00Z',
        }),
      ]),
    );

    renderApp('/reservations');

    const items = await page().findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByRole('heading', { name: '한여름 밤의 꿈' })).toBeInTheDocument();
    expect(items[0]).toHaveTextContent('2027. 1. 16.(토) 00:30 · 혜화 소극장');
    expect(items[0]).toHaveTextContent('좌석 B3');
    expect(items[0]).toHaveTextContent('금액 45,000원');
    expect(within(items[1]).getByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
    expect(items[1]).toHaveTextContent('2026. 12. 20.(일) 19:00 · 대학로 커튼홀');
    expect(items[1]).toHaveTextContent('좌석 A1, A2');
    expect(items[1]).toHaveTextContent('금액 60,000원');
  });

  it('응답을 받기 전에는 "불러오는 중입니다."를 보여 주고, 받으면 지운다', async () => {
    saveAccessToken('token-1');
    let release!: () => void;
    const released = new Promise<void>((resolve) => (release = resolve));
    server.use(
      http.get(RESERVATIONS_PATH, async () => {
        await released;
        return HttpResponse.json([aReservation({ performance: aPerformance({ title: '작은 방의 햄릿' }) })]);
      }),
    );
    renderApp('/reservations');

    expect(await screen.findByRole('status')).toHaveTextContent('불러오는 중입니다.');
    expect(page().queryByRole('listitem')).not.toBeInTheDocument();

    release();

    expect(await screen.findByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
    expect(screen.queryByText('불러오는 중입니다.')).not.toBeInTheDocument();
  });

  it('예매가 없으면 "예매 내역이 없습니다."와 공연 목록 링크를 보여 주고, 링크를 누르면 공연 목록으로 간다', async () => {
    saveAccessToken('token-1');
    server.use(reservationsHandler([]), performancesHandler([]));
    const { router, user } = renderApp('/reservations');

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예매 내역이 없습니다.'));
    expect(page().queryByRole('list')).not.toBeInTheDocument();
    const link = page().getByRole('link', { name: '공연 목록' });
    expect(link).toHaveAttribute('href', '/');

    await user.click(link);

    expect(router.state.location.pathname).toBe('/');
    expect(await screen.findByRole('heading', { level: 1, name: '공연 목록' })).toBeInTheDocument();
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['응답 없음(네트워크 오류)', () => HttpResponse.error(), '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
  ])('%s면 code별 안내 문구를 role="alert"로 보여 주고 목록·빈 목록 문구는 보여 주지 않는다', async (_name, respond, message) => {
    saveAccessToken('token-1');
    server.use(http.get(RESERVATIONS_PATH, respond));

    renderApp('/reservations');

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(page().queryByRole('list')).not.toBeInTheDocument();
    expect(page().queryByRole('link', { name: '공연 목록' })).not.toBeInTheDocument();
  });

  it('R6: 내 예매 요청이 401 AUTH_REQUIRED면 화면이 따로 처리하지 않아도 공통 처리로 로그인 화면에 가고, 로그인하면 내 예매로 돌아온다', async () => {
    saveAccessToken('expired-token');
    server.use(
      http.get(RESERVATIONS_PATH, ({ request }) =>
        request.headers.get('Authorization') === 'Bearer new-token'
          ? HttpResponse.json([aReservation({ performance: aPerformance({ title: '작은 방의 햄릿' }) })])
          : problem(401, 'AUTH_REQUIRED'),
      ),
    );
    const { router, user } = renderApp('/reservations');

    // 내 예매 화면도 받은 오류의 문구를 잠시 보여 줄 수 있으므로, 로그인 화면이 그려진 뒤에 문구를 확인한다.
    expect(await screen.findByLabelText('아이디')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요합니다.');
    expect(getAccessToken()).toBeNull();

    await login(user, 'new-token');

    expect(await screen.findByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/reservations');
  });
});
