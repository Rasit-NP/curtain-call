import { act, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi, type MockInstance } from 'vitest';
import { apiRequest } from '../api/client';
import { PROTECTED_TEST_PATH, renderApp } from '../test/renderApp';
import { server } from '../test/server';
import { getAccessToken, saveAccessToken } from './tokenStorage';

// 401 AUTH_REQUIRED 공통 처리(spec 3.3). 화면은 오류를 받기만 하고, 토큰 삭제·로그인 이동은 공통 처리가 한다.

function reservationsRespond(status: number, code: string) {
  server.use(http.get('/api/reservations', () => HttpResponse.json({ status, code }, { status })));
}

/**
 * 지금까지 보낸 요청의 응답을 클라이언트가 모두 처리할 때까지 기다린다.
 * 처리 결과가 '아무 일도 없음'인 경우(토큰을 지우지 않음, 이동하지 않음)를 검사하려면 처리가 끝났다는 것을 먼저 알아야 한다.
 */
async function responsesProcessed(fetchSpy: MockInstance<typeof fetch>) {
  await Promise.allSettled(fetchSpy.mock.results.map((result) => result.value as Promise<Response>));
  // 응답 본문을 읽고 오류를 처리하는 데 남은 비동기 단계를 마저 돌린다.
  await new Promise((resolve) => setTimeout(resolve, 20));
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
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const { router, user } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);
    await user.click(screen.getByRole('button', { name: '내 예매 두 번 불러오기' }));
    await screen.findByRole('button', { name: '로그인' });
    // 두 번째 요청이 핸들러에 닿기 전에 풀면 늦게 온 응답의 경우를 거치지 않고 통과하므로, 닿을 때까지 기다린다.
    await vi.waitFor(() => expect(releaseSecond).toBeDefined());

    releaseSecond?.();
    await responsesProcessed(fetchSpy);
    await vi.waitFor(() => expect(router.state.navigation.state).toBe('idle'));
    await login(user);

    expect(await screen.findByText(`경로: ${PROTECTED_TEST_PATH}?page=2`)).toBeInTheDocument();
  });

  it('R6: 다시 로그인한 뒤에 끝난 이전 요청의 AUTH_REQUIRED는 새 토큰을 지우거나 로그인 화면으로 다시 보내지 않는다', async () => {
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
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const { router, user } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);
    await user.click(screen.getByRole('button', { name: '내 예매 두 번 불러오기' }));
    await screen.findByRole('button', { name: '로그인' });
    await vi.waitFor(() => expect(releaseSecond).toBeDefined());
    await login(user);
    await screen.findByText(`경로: ${PROTECTED_TEST_PATH}?page=2`);

    releaseSecond?.();
    await responsesProcessed(fetchSpy);

    expect(getAccessToken()).toBe('new-token');
    expect(router.state.location.pathname).toBe(PROTECTED_TEST_PATH);
    expect(screen.getByText(`경로: ${PROTECTED_TEST_PATH}?page=2`)).toBeInTheDocument();
  });

  it('R6: 로그인 화면 경로 끝에 /가 붙어 있어도(/login/) 늦게 온 AUTH_REQUIRED가 돌아갈 경로를 덮지 않는다', async () => {
    reservationsRespond(401, 'AUTH_REQUIRED');
    const { router, user } = renderApp({ pathname: '/login/', state: { from: `${PROTECTED_TEST_PATH}?page=2` } });

    await act(async () => {
      await apiRequest('/api/reservations', { auth: true }).catch(() => undefined);
    });
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

  it('R6: 인증이 필요한 API의 다른 오류(500)에는 토큰을 지우거나 화면을 옮기지 않는다', async () => {
    saveAccessToken('token-1');
    reservationsRespond(500, 'INTERNAL_ERROR');
    const { router, user } = renderApp(PROTECTED_TEST_PATH);

    await user.click(screen.getByRole('button', { name: '내 예매 불러오기' }));

    expect(await screen.findByText('실패: INTERNAL_ERROR')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PROTECTED_TEST_PATH);
    expect(getAccessToken()).toBe('token-1');
  });
});
