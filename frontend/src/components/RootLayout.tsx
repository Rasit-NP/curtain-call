import { Outlet } from 'react-router';
import { AuthRequiredRedirect } from '../auth/AuthRequiredRedirect';
import { Header } from './Header';

/**
 * 모든 화면의 공통 레이아웃. 공통 헤더(로그인 화면 포함)와 401 AUTH_REQUIRED 공통 처리(AuthRequiredRedirect)를 여기에 한 번 둔다.
 */
export function RootLayout() {
  return (
    <>
      <Header />
      <main>
        <AuthRequiredRedirect />
        <Outlet />
      </main>
    </>
  );
}
