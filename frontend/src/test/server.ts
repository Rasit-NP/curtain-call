import { setupServer } from 'msw/node';

// 테스트마다 server.use(...)로 필요한 핸들러만 더한다. 기본 핸들러는 두지 않는다.
export const server = setupServer();

/**
 * 핸들러가 없는 요청은 실패시키고(listen의 'error' 전략, setup.ts) 여기서 기록한다.
 * - MSW 3의 'error' 전략은 요청을 네트워크 오류로 끝내 실제 네트워크로 보내지 않는다.
 *   커스텀 onUnhandledFrame 콜백은 정상으로 끝나면 요청을 실제 네트워크로 보내고(passthrough),
 *   예외를 던지면 500 응답으로 바꾸므로 쓰지 않는다. 콜백의 `defaults.error()`는 메시지만 찍는다.
 * - 요청만 실패시키면 API 클라이언트가 이를 '응답 없음'으로 바꿔, 응답 없음을 기대하는 테스트가 핸들러 없이도 통과할 수 있다.
 *   그래서 기록을 남겨 lifecycle.ts가 테스트를 실패로 만든다.
 */
export const UNHANDLED_REQUEST_STRATEGY = 'error';

// MSW는 요청을 비동기로 처리한다. 테스트가 끝날 때 아직 처리 중인 요청을 기다리려고, 시작했지만 끝나지 않은 요청을 센다.
const inFlightRequests = new Set<string>();
const unhandledRequests: string[] = [];
server.events.on('request:unhandled', ({ request, requestId }) => {
  unhandledRequests.push(`${request.method} ${request.url}`);
  // 'error' 전략으로 끝난 요청에는 MSW가 request:end를 보내지 않으므로 여기서 처리 중 목록에서 뺀다.
  inFlightRequests.delete(requestId);
});

/** 기록된 '핸들러 없는 요청'을 돌려주고 기록을 비운다. */
export function takeUnhandledRequests(): string[] {
  return unhandledRequests.splice(0);
}

server.events.on('request:start', ({ requestId }) => {
  inFlightRequests.add(requestId);
});
server.events.on('request:end', ({ requestId }) => {
  inFlightRequests.delete(requestId);
});

/**
 * 처리 중인 요청이 끝날 때까지 기다린다. 최소한 매크로태스크 한 번은 기다려, 테스트 끝에 막 보낸 요청도 처리가 시작되게 한다.
 * 응답하지 않는 핸들러(시간 제한 테스트)는 끝나지 않으므로 maxWaitMs까지만 기다리고, 그 요청은 더 세지 않는다.
 */
export async function settleRequests(maxWaitMs = 200): Promise<void> {
  const deadline = Date.now() + maxWaitMs;
  do {
    await new Promise((resolve) => setTimeout(resolve, 0));
  } while (inFlightRequests.size > 0 && Date.now() < deadline);
  inFlightRequests.clear();
}
