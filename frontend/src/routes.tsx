import type { RouteObject } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { LOGIN_PATH } from './auth/loginRedirect';
import { RootLayout } from './components/RootLayout';
import { LoginPage } from './pages/login/LoginPage';
import { PerformanceListPage } from './pages/performances/PerformanceListPage';
import { MyReservationsPage } from './pages/reservations/MyReservationsPage';
import { SeatSelectRoute } from './pages/seat/SeatSelectPage';
import { PERFORMANCE_PATH, RESERVATIONS_PATH } from './paths';

/** 로그인이 필요 없는 화면(spec 7.1) */
const publicRoutes: RouteObject[] = [
  { index: true, element: <PerformanceListPage /> },
  { path: LOGIN_PATH.slice(1), element: <LoginPage /> },
];

/** 로그인이 필요한 화면(좌석 선택, 결제·예매 확인, 내 예매). 화면 Issue에서 여기에 더한다(spec 7.1, R6). */
const protectedRoutes: RouteObject[] = [
  { path: PERFORMANCE_PATH.slice(1), element: <SeatSelectRoute /> },
  { path: RESERVATIONS_PATH.slice(1), element: <MyReservationsPage /> },
];

export interface CreateRoutesOptions {
  /** 테스트에서 가드 아래에 더할 화면. 실제 화면이 생기기 전에 가드를 검증하려고 둔다. */
  protectedChildren?: RouteObject[];
}

export function createRoutes({ protectedChildren = [] }: CreateRoutesOptions = {}): RouteObject[] {
  return [
    {
      path: '/',
      element: <RootLayout />,
      children: [...publicRoutes, { element: <RequireAuth />, children: [...protectedRoutes, ...protectedChildren] }],
    },
  ];
}

export const routes: RouteObject[] = createRoutes();
