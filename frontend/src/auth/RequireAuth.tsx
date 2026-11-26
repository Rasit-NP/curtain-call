import { Navigate, Outlet, useLocation } from 'react-router';
import { LOGIN_PATH, loginRedirectState } from './loginRedirect';
import { getAccessToken } from './tokenStorage';

/**
 * 로그인이 필요한 화면(좌석 선택, 결제·예매 확인, 내 예매)을 감싸는 가드(spec 7.1, R6).
 * 토큰이 없으면 로그인 화면으로 보내고, 로그인하면 지금 경로(쿼리 포함)로 돌아오게 한다.
 * 토큰이 만료됐는지는 알 수 없으므로 서버의 401 AUTH_REQUIRED 공통 처리(AuthRequiredRedirect)에 맡긴다.
 */
export function RequireAuth() {
  const location = useLocation();
  if (getAccessToken() === null) {
    return <Navigate to={LOGIN_PATH} replace state={loginRedirectState(location)} />;
  }
  return <Outlet />;
}
