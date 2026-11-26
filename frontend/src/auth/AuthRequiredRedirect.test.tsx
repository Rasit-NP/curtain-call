import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { PROTECTED_TEST_PATH, renderApp } from '../test/renderApp';
import { server } from '../test/server';
import { getAccessToken, saveAccessToken } from './tokenStorage';

// 401 AUTH_REQUIRED 공통 처리(spec 3.3). 화면은 오류를 받기만 하고, 토큰 삭제·로그인 이동은 공통 처리가 한다.

function reservationsRespond(status: number, code: string) {
  server.use(http.get('/api/reservations', () => HttpResponse.json({ status, code }, { status })));
}

async function login(user: ReturnType<typeof renderApp>['user']) {
  server.use(http.post('/api/auth/login', () => HttpResponse.json({ accessToken: 'new-token', expiresIn: 3600 })));
  await user.type(screen.getByLabelText('아이디'), 'user1');
  await user.type(screen.getByLabelText('비밀번호'), 'password1');
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

describe('401 AUTH_REQUIRED 공통 처리', () => {
  it('R6: API가 401 AUTH_REQUIRED를 주면 토큰을 지우고 로그인 화면으로 보내며 로그인이 필요하다는 문구를 보여 준다', async () => {
    saveAccessToken('expired-token');
    reservationsRespond(401, 'AUTH_REQUIRED');
    const { router, user } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);

    await user.click(screen.getByRole('button', { name: '내 예매 불러오기' }));

    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요합니다.');
    expect(getAccessToken()).toBeNull();
  });

  it('R6: AUTH_REQUIRED로 로그인 화면에 온 뒤 로그인하면 원래 화면(쿼리 포함)으로 돌아온다', async () => {
    saveAccessToken('expired-token');
    reservationsRespond(401, 'AUTH_REQUIRED');
    const { user } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);
    await user.click(screen.getByRole('button', { name: '내 예매 불러오기' }));
    await screen.findByRole('button', { name: '로그인' });

    await login(user);

    expect(await screen.findByText(`경로: ${PROTECTED_TEST_PATH}?page=2`)).toBeInTheDocument();
    expect(getAccessToken()).toBe('new-token');
  });

  it('R6: 요청 두 개가 AUTH_REQUIRED를 받고 두 번째가 로그인 화면에 온 뒤에 끝나도, 로그인하면 원래 화면으로 돌아온다', async () => {
    saveAccessToken('expired-token');
    let releaseSecond: (() => void) | undefined;
    let calls = 0;
    server.use(
      http.get('/api/reservations', () => {
        calls += 1;
        const response = HttpResponse.json({ status: 401, code: 'AUTH_REQUIRED' }, { status: 401 });
        if (calls === 1) return response;
        return new Promise<Response>((resolve) => {
          releaseSecond = () => resolve(response);
        });
      }),
    );
    const { router, user } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);
    await user.click(screen.getByRole('button', { name: '내 예매 두 번 불러오기' }));
    await screen.findByRole('button', { name: '로그인' });

    releaseSecond?.();
    await vi.waitFor(() => expect(calls).toBe(2));
    await vi.waitFor(() => expect(router.state.navigation.state).toBe('idle'));
    await login(user);

    expect(await screen.findByText(`경로: ${PROTECTED_TEST_PATH}?page=2`)).toBeInTheDocument();
  });

  it('R6: 인증이 필요한 API의 다른 401(INVALID_CREDENTIALS)에는 토큰을 지우거나 화면을 옮기지 않는다', async () => {
    saveAccessToken('token-1');
    reservationsRespond(401, 'INVALID_CREDENTIALS');
    const { router, user } = renderApp(PROTECTED_TEST_PATH);

    await user.click(screen.getByRole('button', { name: '내 예매 불러오기' }));

    expect(await screen.findByText('실패: INVALID_CREDENTIALS')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PROTECTED_TEST_PATH);
    expect(getAccessToken()).toBe('token-1');
  });

  it('다른 오류(500)에는 토큰을 지우거나 화면을 옮기지 않는다', async () => {
    saveAccessToken('token-1');
    reservationsRespond(500, 'INTERNAL_ERROR');
    const { router, user } = renderApp(PROTECTED_TEST_PATH);

    await user.click(screen.getByRole('button', { name: '내 예매 불러오기' }));

    expect(await screen.findByText('실패: INTERNAL_ERROR')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PROTECTED_TEST_PATH);
    expect(getAccessToken()).toBe('token-1');
  });
});
