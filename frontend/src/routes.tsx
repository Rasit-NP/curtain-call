import { Outlet, type RouteObject } from 'react-router';

// 화면(로그인, 공연 목록, 좌석 선택, 결제·예매 확인, 내 예매)은 화면 Issue에서 children에 더한다(spec 7.1).
export const routes: RouteObject[] = [
  {
    path: '/',
    element: (
      <main>
        <Outlet />
      </main>
    ),
    children: [],
  },
];
