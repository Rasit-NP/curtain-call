import { Outlet } from 'react-router';
import { AuthRequiredRedirect } from '../auth/AuthRequiredRedirect';

/** 모든 화면의 공통 레이아웃. 401 AUTH_REQUIRED 공통 처리(AuthRequiredRedirect)를 여기에 한 번 둔다. */
export function RootLayout() {
  return (
    <main>
      <AuthRequiredRedirect />
      <Outlet />
    </main>
  );
}
