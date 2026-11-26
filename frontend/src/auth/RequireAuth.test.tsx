import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { performancesHandler } from '../test/handlers';
import { PROTECTED_TEST_PATH, renderApp } from '../test/renderApp';
import { server } from '../test/server';
import { getAccessToken, saveAccessToken } from './tokenStorage';

// 로그인이 필요한 실제 화면(좌석 선택, 결제·예매 확인, 내 예매)은 화면 Issue(2~4단계)가 만든다.
// 여기서는 같은 가드 아래에 둔 테스트용 화면으로 가드 자체를 검증한다.

async function login(user: ReturnType<typeof renderApp>['user']) {
  await user.type(screen.getByLabelText('아이디'), 'user1');
  await user.type(screen.getByLabelText('비밀번호'), 'password1');
  await user.click(screen.getByRole('button', { name: '로그인' }));
}

function loginSucceeds() {
  server.use(http.post('/api/auth/login', () => HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 })));
}

describe('인증 가드', () => {
  it('R6: 토큰이 있으면 로그인이 필요한 화면을 그대로 보여 준다', () => {
    saveAccessToken('token-1');

    const { router } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);

    expect(screen.getByRole('heading', { name: '보호된 테스트 화면' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PROTECTED_TEST_PATH);
  });

  it('R6: 토큰 없이 로그인이 필요한 화면에 들어가면 로그인 화면으로 보내고, 로그인하면 원래 경로(쿼리 포함)로 돌아온다', async () => {
    loginSucceeds();
    const { router, user } = renderApp(`${PROTECTED_TEST_PATH}?page=2&sort=desc`);

    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('heading', { name: '보호된 테스트 화면' })).not.toBeInTheDocument();

    await login(user);

    expect(await screen.findByRole('heading', { name: '보호된 테스트 화면' })).toBeInTheDocument();
    expect(screen.getByText(`경로: ${PROTECTED_TEST_PATH}?page=2&sort=desc`)).toBeInTheDocument();
    expect(getAccessToken()).toBe('token-1');
  });

  it('R6: 가드가 보낸 로그인 화면에서 한 번 실패해도, 다시 로그인하면 원래 경로로 돌아온다', async () => {
    let calls = 0;
    server.use(
      http.post('/api/auth/login', () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json({ status: 401, code: 'INVALID_CREDENTIALS' }, { status: 401 })
          : HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 });
      }),
    );
    const { user } = renderApp(`${PROTECTED_TEST_PATH}?page=2`);
    await login(user);
    expect(await screen.findByRole('alert')).toHaveTextContent('아이디 또는 비밀번호가 올바르지 않습니다.');

    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText(`경로: ${PROTECTED_TEST_PATH}?page=2`)).toBeInTheDocument();
  });

  it('로그인 뒤 돌아갈 경로가 앱 안의 경로가 아니면 공연 목록(/)으로 간다', async () => {
    loginSucceeds();
    // 로그인 뒤 가는 공연 목록 화면이 부르는 API
    server.use(performancesHandler([]));
    const { router, user } = renderApp({ pathname: '/login', state: { from: '//evil.example/steal' } });

    await login(user);

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });
});
