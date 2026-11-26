import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { server } from '../test/server';
import { login } from './auth';

describe('로그인 API', () => {
  it('아이디와 비밀번호를 POST /api/auth/login으로 보내고 토큰 응답을 돌려준다', async () => {
    let received: unknown;
    server.use(
      http.post('/api/auth/login', async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 });
      }),
    );

    await expect(login({ username: 'user1', password: 'password1' })).resolves.toEqual({
      accessToken: 'token-1',
      expiresIn: 3600,
    });
    expect(received).toEqual({ username: 'user1', password: 'password1' });
  });

  it('R6: 로그인 요청에는 저장된 토큰이 있어도 Authorization 헤더를 붙이지 않는다', async () => {
    saveAccessToken('stale-token');
    let authorization: string | null = 'not-called';
    server.use(
      http.post('/api/auth/login', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json({ accessToken: 'token-1', expiresIn: 3600 });
      }),
    );

    await login({ username: 'user1', password: 'password1' });

    expect(authorization).toBeNull();
  });
});
