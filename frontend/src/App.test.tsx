import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { routes } from './routes';

describe('라우터', () => {
  it('첫 화면 경로(/)에서 오류 없이 공통 레이아웃을 그린다', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] });

    render(<RouterProvider router={router} />);

    expect(screen.getByRole('main')).toBeInTheDocument();
  });
});
