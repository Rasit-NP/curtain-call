import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { createMemoryRouter, RouterProvider, type InitialEntry } from 'react-router';
import { createRoutes } from '../routes';
import { ProtectedTestPage } from './ProtectedTestPage';

/** 가드 뒤의 테스트용 화면 경로. 실제 화면은 화면 Issue(2~4단계)가 만든다. */
export const PROTECTED_TEST_PATH = '/test/protected';

interface RenderAppOptions {
  /** 가짜 타이머를 쓰는 테스트는 user-event가 타이머를 앞당기게 넘긴다. */
  advanceTimers?: (ms: number) => unknown;
  /** 개발 서버(main.tsx)처럼 StrictMode로 그린다. effect가 한 번 더 실행돼도 동작이 같은지 확인할 때 쓴다. */
  strict?: boolean;
}

/**
 * 실제 라우트 구성(createRoutes)에 테스트용 보호 화면을 더해 initialEntry에서 시작한다.
 * 기록을 여러 개 넘기면 마지막 기록에서 시작한다(브라우저 뒤로가기를 흉내 낼 때 쓴다).
 */
export function renderApp(initialEntry: InitialEntry | InitialEntry[], options: RenderAppOptions = {}) {
  const initialEntries = Array.isArray(initialEntry) ? initialEntry : [initialEntry];
  const router = createMemoryRouter(
    createRoutes({ protectedChildren: [{ path: PROTECTED_TEST_PATH.slice(1), element: <ProtectedTestPage /> }] }),
    { initialEntries, initialIndex: initialEntries.length - 1 },
  );
  const user = userEvent.setup(options.advanceTimers ? { advanceTimers: options.advanceTimers } : {});
  const app = <RouterProvider router={router} />;
  render(options.strict ? <StrictMode>{app}</StrictMode> : app);
  return { router, user };
}
