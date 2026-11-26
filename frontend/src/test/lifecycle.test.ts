import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { finishTest } from './lifecycle';
import { server, takeUnhandledRequests } from './server';

describe('테스트 설정: 핸들러가 없는 요청', () => {
  let realServer: Server | undefined;

  afterEach(async () => {
    await new Promise<void>((resolve) => (realServer ? realServer.close(() => resolve()) : resolve()));
    realServer = undefined;
  });

  it('핸들러가 없는 요청은 실제 네트워크로 나가지 않고 실패한다', async () => {
    let hits = 0;
    realServer = createServer((_request, response) => {
      hits += 1;
      response.end('real');
    });
    await new Promise<void>((resolve) => realServer!.listen(0, '127.0.0.1', resolve));
    const { port } = realServer.address() as AddressInfo;

    await expect(fetch(`http://127.0.0.1:${port}/api/performances`)).rejects.toThrow();

    expect(hits).toBe(0);
    expect(takeUnhandledRequests()).toEqual([`GET http://127.0.0.1:${port}/api/performances`]);
  });

  it('테스트가 끝날 때 핸들러가 없는 요청이 있었으면 테스트를 실패로 만든다', async () => {
    await fetch('/api/reservations').catch(() => {});

    await expect(finishTest()).rejects.toThrow('GET http://localhost:3000/api/reservations');
  });

  it('테스트가 끝날 때 아직 처리 중인 요청도 검사한다', async () => {
    void fetch('/api/reservations').catch(() => {});

    await expect(finishTest()).rejects.toThrow('GET http://localhost:3000/api/reservations');
  });

  it('테스트가 끝날 때 처리 중인 요청은 그 테스트의 핸들러로 처리한다', async () => {
    server.use(http.get('/api/performances', () => HttpResponse.json(['이 테스트의 응답'])));
    const response = fetch('/api/performances').then((result) => result.json());

    await finishTest();

    await expect(response).resolves.toEqual(['이 테스트의 응답']);
  });
});
