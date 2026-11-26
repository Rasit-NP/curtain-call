import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { routes } from './routes';
import { performancesHandler } from './test/handlers';
import { server } from './test/server';

describe('라우터', () => {
  it('첫 화면 경로(/)에서 오류 없이 공통 레이아웃을 그린다', () => {
    // 첫 화면(공연 목록)이 부르는 API
    server.use(performancesHandler([]));
    const router = createMemoryRouter(routes, { initialEntries: ['/'] });

    render(<RouterProvider router={router} />);

    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('/login 경로에서 로그인 화면을 그린다', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/login'] });

    render(<RouterProvider router={router} />);

    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });
});
