import { setupServer } from 'msw/node';

// 테스트마다 server.use(...)로 필요한 핸들러만 더한다. 기본 핸들러는 두지 않는다.
export const server = setupServer();

type ListenOptions = NonNullable<Parameters<typeof server.listen>[0]>;

const unhandledRequests: string[] = [];

/**
 * 핸들러가 없는 요청(MSW 3의 'frame')을 기록하고 그 요청을 실패시킨다.
 * 요청만 실패시키면 API 클라이언트가 이를 '응답 없음'으로 바꿔, 응답 없음을 기대하는 테스트가
 * 핸들러 없이도 통과할 수 있다. 그래서 기록을 남겨 setup.ts의 afterEach가 테스트를 실패로 만든다.
 */
export const onUnhandledFrame: ListenOptions['onUnhandledFrame'] = ({ frame, defaults }) => {
  if (frame.protocol === 'http') {
    const { request } = frame.data as { request: Request };
    unhandledRequests.push(`${request.method} ${request.url}`);
  } else {
    unhandledRequests.push(`${frame.protocol} frame`);
  }
  defaults.error();
};

/** 기록된 '핸들러 없는 요청'을 돌려주고 기록을 비운다. */
export function takeUnhandledRequests(): string[] {
  return unhandledRequests.splice(0);
}
