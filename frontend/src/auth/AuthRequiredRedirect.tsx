import { useEffect, useRef } from 'react';
import { matchPath, useLocation, useNavigate } from 'react-router';
import { onAuthRequired } from './authRequired';
import { LOGIN_PATH, loginRedirectState } from './loginRedirect';

/**
 * 401 AUTH_REQUIRED 공통 처리의 화면 이동 부분(spec 3.3, R6). 라우터 최상위 레이아웃에 한 번 둔다.
 * API 클라이언트가 토큰을 지우고 알리면, 지금 화면으로 돌아오도록 state를 담아 로그인 화면으로 보낸다.
 * 화면들은 AUTH_REQUIRED를 따로 처리하지 않는다.
 */
export function AuthRequiredRedirect() {
  const navigate = useNavigate();
  const location = useLocation();
  // 알림은 렌더링 밖에서 오므로 마지막으로 그린 화면의 경로를 ref로 읽는다.
  const locationRef = useRef(location);

  useEffect(() => {
    locationRef.current = location;
  }, [location]);

  useEffect(
    () =>
      onAuthRequired(() => {
        const current = locationRef.current;
        // 로그인 화면에서 끝난 요청이면 이미 로그인 화면이다. 다시 보내면 돌아갈 경로가 /login으로 바뀐다.
        // 라우터와 같은 규칙(끝의 /, 대소문자 무시)으로 판단해야 /login/ 같은 경로도 로그인 화면으로 본다.
        if (matchPath(LOGIN_PATH, current.pathname) !== null) return;
        // 요청 여러 개가 함께 AUTH_REQUIRED를 받으면 같은 state로 여러 번 부를 수 있다. replace라 기록이 쌓이지 않는다.
        void navigate(LOGIN_PATH, { replace: true, state: loginRedirectState(current, 'AUTH_REQUIRED') });
      }),
    [navigate],
  );

  return null;
}
