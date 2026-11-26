import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { apiRequest } from '../api/client';
import { getAccessToken, saveAccessToken } from '../auth/tokenStorage';
import { performancesHandler, problem, RESERVATIONS_PATH, reservationsHandler } from '../test/handlers';
import { renderApp } from '../test/renderApp';
import { server } from '../test/server';

/** 공통 헤더(banner) */
function header() {
  return within(screen.getByRole('banner'));
}

async function login(user: ReturnType<typeof renderApp>['user']) {
  server.use(http.post('/api/auth/login', () => HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 })));
  // 라우터는 화면 이동을 transition으로 그려, 위치가 바뀐 뒤에도 잠시 이전 화면이 남아 있을 수 있다.
  await user.type(await screen.findByLabelText('아이디'), 'user1');
  await user.type(screen.getByLabelText('비밀번호'), 'password1');
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

describe('공통 헤더', () => {
  it('로그인 전에는 커튼콜(공연 목록)·내 예매·로그인 링크를 보여 주고 로그아웃은 없다', async () => {
    server.use(performancesHandler([]));

    renderApp('/');

    expect(header().getByRole('link', { name: '커튼콜' })).toHaveAttribute('href', '/');
    expect(header().getByRole('link', { name: '내 예매' })).toHaveAttribute('href', '/reservations');
    expect(header().getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
    expect(header().queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예정된 공연이 없습니다.'));
  });

  it('로그인한 뒤에는 로그인 대신 로그아웃 버튼을 보여 준다', async () => {
    saveAccessToken('token-1');
    server.use(performancesHandler([]));

    renderApp('/');

    expect(header().getByRole('link', { name: '커튼콜' })).toBeInTheDocument();
    expect(header().getByRole('link', { name: '내 예매' })).toBeInTheDocument();
    expect(header().getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
    expect(header().queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예정된 공연이 없습니다.'));
  });

  it('로그인하면 헤더가 바로 로그아웃으로 바뀐다', async () => {
    server.use(performancesHandler([]));
    const { user } = renderApp('/login');
    expect(header().getByRole('link', { name: '로그인' })).toBeInTheDocument();

    await login(user);

    expect(await header().findByRole('button', { name: '로그아웃' })).toBeInTheDocument();
    expect(header().queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
  });

  it('헤더의 로그인을 눌러 로그인하면 보던 화면(쿼리 포함)으로 돌아온다', async () => {
    server.use(performancesHandler([]));
    const { router, user } = renderApp('/?sort=latest');

    await user.click(header().getByRole('link', { name: '로그인' }));
    expect(router.state.location.pathname).toBe('/login');

    await login(user);

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(router.state.location.search).toBe('?sort=latest');
  });

  it('로그인 화면에도 헤더가 있고, 로그인 링크는 지금 화면으로 표시되며 눌러도 돌아갈 화면을 잃지 않는다', async () => {
    server.use(reservationsHandler([]));
    const { router, user } = renderApp('/reservations');
    expect(await screen.findByLabelText('아이디')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');

    const loginLink = header().getByRole('link', { name: '로그인' });
    expect(header().getByRole('link', { name: '커튼콜' })).toBeInTheDocument();
    expect(loginLink).toHaveAttribute('aria-current', 'page');

    await user.click(loginLink);
    expect(router.state.location.pathname).toBe('/login');
    await login(user);

    expect(await screen.findByRole('heading', { level: 1, name: '내 예매' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/reservations');
  });

  it('R6: 로그인 전에 헤더의 내 예매를 누르면 로그인 화면으로 가고, 로그인하면 내 예매 화면으로 온다', async () => {
    server.use(performancesHandler([]), reservationsHandler([]));
    const { router, user } = renderApp('/');

    await user.click(header().getByRole('link', { name: '내 예매' }));
    expect(router.state.location.pathname).toBe('/login');

    await login(user);

    expect(await screen.findByRole('heading', { level: 1, name: '내 예매' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/reservations');
  });
});

describe('로그아웃', () => {
  it('로그아웃을 누르면 토큰을 지우고 공연 목록(/)으로 가며, 헤더가 로그인으로 바뀌고 안내 문구는 없다', async () => {
    saveAccessToken('token-1');
    server.use(reservationsHandler([]), performancesHandler([]));
    const { router, user } = renderApp('/reservations');
    await screen.findByRole('heading', { level: 1, name: '내 예매' });

    await user.click(header().getByRole('button', { name: '로그아웃' }));

    expect(getAccessToken()).toBeNull();
    expect(router.state.location.pathname).toBe('/');
    expect(header().getByRole('link', { name: '로그인' })).toBeInTheDocument();
    expect(header().queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예정된 공연이 없습니다.'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('공연 목록에서 로그아웃해도 그 화면에 머물며 헤더가 바로 로그인으로 바뀐다', async () => {
    saveAccessToken('token-1');
    server.use(performancesHandler([]));
    const { router, user } = renderApp('/');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예정된 공연이 없습니다.'));

    await user.click(header().getByRole('button', { name: '로그아웃' }));

    expect(getAccessToken()).toBeNull();
    expect(router.state.location.pathname).toBe('/');
    expect(header().getByRole('link', { name: '로그인' })).toBeInTheDocument();
  });
});

describe('401 AUTH_REQUIRED와 헤더', () => {
  it('AUTH_REQUIRED로 토큰이 지워지면 헤더가 바로 로그인으로 바뀐다', async () => {
    saveAccessToken('expired-token');
    server.use(http.get(RESERVATIONS_PATH, () => problem(401, 'AUTH_REQUIRED')));

    const { router } = renderApp('/reservations');

    expect(await header().findByRole('link', { name: '로그인' })).toBeInTheDocument();
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(header().queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('로그인 화면에 있어 화면 이동이 없어도, AUTH_REQUIRED로 토큰이 지워지면 헤더가 바로 로그인으로 바뀐다', async () => {
    saveAccessToken('expired-token');
    server.use(http.get(RESERVATIONS_PATH, () => problem(401, 'AUTH_REQUIRED')));
    const { router } = renderApp('/login');
    expect(header().getByRole('button', { name: '로그아웃' })).toBeInTheDocument();

    await act(async () => {
      await apiRequest(RESERVATIONS_PATH, { auth: true }).catch(() => {});
    });

    expect(getAccessToken()).toBeNull();
    expect(router.state.location.pathname).toBe('/login');
    expect(header().getByRole('link', { name: '로그인' })).toBeInTheDocument();
  });
});
