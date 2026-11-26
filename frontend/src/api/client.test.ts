import { delay, http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { saveAccessToken } from '../auth/tokenStorage';
import { server } from '../test/server';
import { apiRequest, REQUEST_TIMEOUT_MS } from './client';
import { ApiError, NO_RESPONSE } from './errors';

/** 요청을 실패로 끝내고, 거부 사유(ApiError)를 돌려준다. */
async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error('요청이 성공했습니다. 실패를 기대했습니다.');
}

describe('apiRequest 성공 응답', () => {
  it('JSON 응답 본문을 돌려준다', async () => {
    server.use(http.get('/api/performances', () => HttpResponse.json([{ id: 1 }])));

    await expect(apiRequest('/api/performances', { auth: false })).resolves.toEqual([{ id: 1 }]);
  });

  it('본문을 JSON으로 보내고 Content-Type을 붙인다', async () => {
    let received: { contentType: string | null; body: unknown } | undefined;
    server.use(
      http.post('/api/holds', async ({ request }) => {
        received = { contentType: request.headers.get('Content-Type'), body: await request.json() };
        return HttpResponse.json({ id: 7 }, { status: 201 });
      }),
    );

    await apiRequest('/api/holds', { method: 'POST', body: { seatIds: [1, 2] }, auth: true });

    expect(received).toEqual({ contentType: 'application/json', body: { seatIds: [1, 2] } });
  });

  it('204 응답이면 undefined를 돌려준다', async () => {
    server.use(http.delete('/api/holds/7', () => new HttpResponse(null, { status: 204 })));

    await expect(apiRequest('/api/holds/7', { method: 'DELETE', auth: true })).resolves.toBeUndefined();
  });

  it('추가 헤더를 함께 보낸다', async () => {
    let key: string | null = null;
    server.use(
      http.post('/api/payments', ({ request }) => {
        key = request.headers.get('Idempotency-Key');
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );

    await apiRequest('/api/payments', {
      method: 'POST',
      body: { holdId: 7 },
      auth: true,
      headers: { 'Idempotency-Key': 'key-1' },
    });

    expect(key).toBe('key-1');
  });
});

describe('apiRequest 오류 응답', () => {
  it.each([
    [400, 'INVALID_INPUT'],
    [401, 'INVALID_CREDENTIALS'],
    [401, 'AUTH_REQUIRED'],
    [402, 'PAYMENT_FAILED'],
    [404, 'NOT_FOUND'],
    [409, 'SEAT_ALREADY_HELD'],
    [409, 'HOLD_LIMIT_EXCEEDED'],
    [409, 'HOLD_EXPIRED'],
    [500, 'INTERNAL_ERROR'],
  ] as const)('%i %s 응답이면 상태와 code를 호출한 쪽에 넘긴다', async (status, code) => {
    server.use(
      http.post('/api/holds', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Error', status, detail: '설명', instance: '/api/holds', code },
          { status, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await failureOf(apiRequest('/api/holds', { method: 'POST', body: {}, auth: true }));

    expect(error).toMatchObject({ status, code });
  });

  it('code가 없는 ProblemDetail 오류 응답은 INTERNAL_ERROR로 본다', async () => {
    server.use(
      http.get('/api/performances', () =>
        HttpResponse.json({ title: 'Bad Gateway', status: 502 }, { status: 502 }),
      ),
    );

    const error = await failureOf(apiRequest('/api/performances', { auth: false }));

    expect(error).toMatchObject({ status: 502, code: 'INTERNAL_ERROR' });
  });

  it('본문이 JSON이 아닌 오류 응답은 INTERNAL_ERROR로 본다', async () => {
    server.use(
      http.get('/api/performances', () =>
        HttpResponse.html('<html><body>Bad Gateway</body></html>', { status: 502 }),
      ),
    );

    const error = await failureOf(apiRequest('/api/performances', { auth: false }));

    expect(error).toMatchObject({ status: 502, code: 'INTERNAL_ERROR' });
  });

  it('오류 코드 표에 없는 code는 INTERNAL_ERROR로 본다', async () => {
    server.use(
      http.get('/api/performances', () =>
        HttpResponse.json({ status: 418, code: 'I_AM_A_TEAPOT' }, { status: 418 }),
      ),
    );

    const error = await failureOf(apiRequest('/api/performances', { auth: false }));

    expect(error).toMatchObject({ status: 418, code: 'INTERNAL_ERROR' });
  });

  it('성공 응답의 본문이 JSON이 아니면 INTERNAL_ERROR로 본다', async () => {
    server.use(http.get('/api/performances', () => HttpResponse.text('not json')));

    const error = await failureOf(apiRequest('/api/performances', { auth: false }));

    expect(error).toMatchObject({ status: 200, code: 'INTERNAL_ERROR' });
  });

  it('본문을 받는 중에 연결이 끊기면 응답 없음으로 넘긴다', async () => {
    // MSW는 응답 본문 스트림의 오류를 삼키고 받은 데까지로 본문을 끝내므로, 이 경우만 fetch를 직접 흉내 낸다.
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('[{"id":'));
        setTimeout(() => controller.error(new Error('연결 끊김')), 0);
      },
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(body, { headers: { 'Content-Type': 'application/json' } }));

    const error = await failureOf(apiRequest('/api/performances', { auth: false }));

    expect(error).toMatchObject({ status: null, code: NO_RESPONSE });
  });

  it('네트워크 오류는 응답 없음으로 넘긴다', async () => {
    server.use(http.get('/api/performances', () => HttpResponse.error()));

    const error = await failureOf(apiRequest('/api/performances', { auth: false }));

    expect(error).toMatchObject({ status: null, code: NO_RESPONSE });
  });
});

describe('apiRequest 시간 제한(spec 7.5)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  /** apiRequest가 fetch에 넘긴 AbortSignal */
  function signalOf(fetchSpy: { mock: { calls: Parameters<typeof fetch>[] } }) {
    return fetchSpy.mock.calls[0]?.[1]?.signal ?? undefined;
  }

  it('시간 제한은 10초다', () => {
    expect(REQUEST_TIMEOUT_MS).toBe(10_000);
  });

  it('10초 동안 응답이 없으면 요청을 끊고 응답 없음으로 넘긴다', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    server.use(http.post('/api/payments', () => new Promise<never>(() => {})));

    const result = failureOf(apiRequest('/api/payments', { method: 'POST', body: {}, auth: true }));
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS);

    await expect(result).resolves.toMatchObject({ status: null, code: NO_RESPONSE });
    expect(signalOf(fetchSpy)?.aborted).toBe(true);
  });

  it('10초가 되기 직전(9,999ms)에 온 응답은 받는다', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    server.use(
      http.get('/api/performances', async () => {
        await delay(REQUEST_TIMEOUT_MS - 1);
        return HttpResponse.json([]);
      }),
    );

    const result = apiRequest('/api/performances', { auth: false });
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS - 1);

    await expect(result).resolves.toEqual([]);
  });

  it('응답을 받은 뒤에는 10초가 지나도 요청을 끊지 않는다', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    server.use(http.get('/api/performances', () => HttpResponse.json([])));

    await apiRequest('/api/performances', { auth: false });
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS);

    expect(signalOf(fetchSpy)?.aborted).toBe(false);
  });
});

describe('apiRequest 토큰 첨부', () => {
  function captureAuthorization(path: string) {
    const captured: { value: string | null } = { value: null };
    server.use(
      http.get(path, ({ request }) => {
        captured.value = request.headers.get('Authorization');
        return HttpResponse.json({});
      }),
    );
    return captured;
  }

  it('R6: 인증이 필요한 요청에는 저장된 토큰을 Bearer로 붙인다', async () => {
    saveAccessToken('token-abc');
    const authorization = captureAuthorization('/api/reservations');

    await apiRequest('/api/reservations', { auth: true });

    expect(authorization.value).toBe('Bearer token-abc');
  });

  it('R6: 공개 API 요청에는 저장된 토큰이 있어도 붙이지 않는다', async () => {
    saveAccessToken('token-abc');
    const authorization = captureAuthorization('/api/performances');

    await apiRequest('/api/performances', { auth: false });

    expect(authorization.value).toBeNull();
  });

  it('R6: 인증이 필요한 요청이라도 저장된 토큰이 없으면 헤더 없이 보낸다', async () => {
    const authorization = captureAuthorization('/api/reservations');

    await apiRequest('/api/reservations', { auth: true });

    expect(authorization.value).toBeNull();
  });
});

describe('apiRequest 경로', () => {
  it('/api/로 시작하지 않는 경로는 요청하지 않고 거부한다', async () => {
    // @ts-expect-error 경로 타입이 /api/로 시작하는 상대 경로만 받는다.
    await expect(apiRequest('http://localhost:8080/api/performances', { auth: false })).rejects.toThrow(
      '/api/',
    );
  });
});
