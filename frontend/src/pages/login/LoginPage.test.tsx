import { act, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { REQUEST_TIMEOUT_MS } from '../../api/client';
import { getAccessToken, saveAccessToken } from '../../auth/tokenStorage';
import { performancesHandler } from '../../test/handlers';
import { renderApp } from '../../test/renderApp';
import { server } from '../../test/server';

const LOGIN_PATH = '/api/auth/login';

function problem(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } });
}

function respondWith(resolver: Parameters<typeof http.post>[1]) {
  server.use(http.post(LOGIN_PATH, resolver));
}

async function submitLogin(user: ReturnType<typeof renderApp>['user'], username = 'user1', password = 'password1') {
  await user.type(screen.getByLabelText('아이디'), username);
  await user.type(screen.getByLabelText('비밀번호'), password);
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

describe('로그인 화면', () => {
  it('아이디·비밀번호 입력과 로그인 버튼이 있고, 처음에는 안내 문구가 없다', () => {
    renderApp('/login');

    expect(screen.getByLabelText('아이디')).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('로그인에 성공하면 입력한 아이디·비밀번호로 요청하고, 토큰을 저장한 뒤 돌아갈 화면이 없으면 공연 목록(/)으로 간다', async () => {
    let received: unknown;
    respondWith(async ({ request }) => {
      received = await request.json();
      return HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 });
    });
    // 로그인 뒤 가는 공연 목록 화면이 부르는 API
    server.use(performancesHandler([]));
    const { router, user } = renderApp('/login');

    await submitLogin(user, 'user1', 'password1');

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(received).toEqual({ username: 'user1', password: 'password1' });
    expect(getAccessToken()).toBe('token-1');
    expect(screen.queryByRole('button', { name: '로그인' })).not.toBeInTheDocument();
  });

  it('R6: 로그인 실패(401 INVALID_CREDENTIALS)는 토큰을 지우거나 화면을 옮기지 않고 안내 문구만 보여 준다', async () => {
    saveAccessToken('kept-token');
    respondWith(() => problem(401, 'INVALID_CREDENTIALS'));
    const { router, user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('아이디 또는 비밀번호가 올바르지 않습니다.');
    expect(getAccessToken()).toBe('kept-token');
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled();
  });

  it.each([
    ['400 INVALID_INPUT', () => problem(400, 'INVALID_INPUT'), '입력한 내용을 확인해 주세요.'],
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['code가 없는 502', () => new HttpResponse(null, { status: 502 }), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['네트워크 오류(응답 없음)', () => HttpResponse.error(), '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
  ])('%s면 code별 안내 문구를 보여 주고 화면에 머문다', async (_, response, message) => {
    respondWith(response);
    const { router, user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(router.state.location.pathname).toBe('/login');
    expect(getAccessToken()).toBeNull();
  });

  it('로그인 요청이 10초 동안 응답하지 않으면 서버에 연결할 수 없습니다 문구를 보여 준다', async () => {
    // React Testing Library는 user-event 조작 뒤 setTimeout(0)을 기다리는데, vitest의 가짜 타이머는 알아보지 못한다.
    // shouldAdvanceTime으로 가짜 시계가 실제 시간만큼은 흐르게 해 그 대기를 풀고, 10초는 advanceTimersByTimeAsync로 앞당긴다.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true });
    respondWith(() => new Promise<never>(() => {}));
    const { user } = renderApp('/login', { advanceTimers: (ms) => vi.advanceTimersByTime(ms) });

    await submitLogin(user);
    // shouldAdvanceTime 때문에 실제로 흐른 몇 ms만큼 가짜 시계가 더 갈 수 있어, 직전 확인은 여유를 둔다.
    await act(() => vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS - 500));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(500));

    // findBy로 기다리면 기다리는 동안 가짜 시계가 더 흘러 10초보다 늦은 시간 제한도 통과하므로, 바로 확인한다.
    expect(screen.getByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled();
  });

  it('로그인 요청 중에는 로그인 버튼을 누를 수 없고, 다시 누르면 이전 안내 문구를 지운다', async () => {
    let release: (() => void) | undefined;
    let calls = 0;
    respondWith(() => {
      calls += 1;
      if (calls === 1) return problem(401, 'INVALID_CREDENTIALS');
      return new Promise<Response>((resolve) => {
        release = () => resolve(HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 }));
      });
    });
    // 로그인 뒤 가는 공연 목록 화면이 부르는 API
    server.use(performancesHandler([]));
    const { router, user } = renderApp('/login');
    await submitLogin(user);
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: '로그인' }));

    await vi.waitFor(() => expect(release).toBeDefined());
    expect(screen.getByRole('button', { name: '로그인' })).toBeDisabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    release?.();
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(calls).toBe(2);
  });
});
