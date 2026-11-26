import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider, type InitialEntry } from 'react-router';
import { createRoutes } from '../routes';
import { ProtectedTestPage } from './ProtectedTestPage';

/** 가드 뒤의 테스트용 화면 경로. 실제 화면은 화면 Issue(2~4단계)가 만든다. */
export const PROTECTED_TEST_PATH = '/test/protected';

interface RenderAppOptions {
  /** 가짜 타이머를 쓰는 테스트는 user-event가 타이머를 앞당기게 넘긴다. */
  advanceTimers?: (ms: number) => unknown;
}

/** 실제 라우트 구성(createRoutes)에 테스트용 보호 화면을 더해 initialEntry에서 시작한다. */
export function renderApp(initialEntry: InitialEntry, options: RenderAppOptions = {}) {
  const router = createMemoryRouter(
    createRoutes({ protectedChildren: [{ path: PROTECTED_TEST_PATH.slice(1), element: <ProtectedTestPage /> }] }),
    { initialEntries: [initialEntry] },
  );
  const user = userEvent.setup(options.advanceTimers ? { advanceTimers: options.advanceTimers } : {});
  render(<RouterProvider router={router} />);
  return { router, user };
}
