import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiRequest } from '../api/client';
import { server, takeUnhandledRequests } from './server';

describe('테스트 설정', () => {
  it('핸들러가 없는 요청은 실패하고, 테스트를 실패로 만들도록 기록된다', async () => {
    server.use(http.get('/api/performances', () => HttpResponse.json([])));

    await expect(apiRequest('/api/reservations', { auth: true })).rejects.toThrow();

    // setup.ts의 afterEach가 이 기록을 보고 테스트를 실패시킨다. 여기서는 기록을 확인하고 비운다.
    expect(takeUnhandledRequests()).toEqual(['GET http://localhost:3000/api/reservations']);
  });
});
