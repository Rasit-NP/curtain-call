import { Link, matchPath, NavLink, useLocation, useNavigate } from 'react-router';
import { LOGIN_PATH, loginRedirectState } from '../auth/loginRedirect';
import { clearAccessToken } from '../auth/tokenStorage';
import { useAccessToken } from '../auth/useAccessToken';
import { HOME_PATH, RESERVATIONS_PATH } from '../paths';
import styles from './Header.module.css';

/**
 * 모든 화면 위의 공통 헤더(spec 7.8). '커튼콜'(공연 목록), '내 예매', 로그인 전에는 '로그인', 로그인 뒤에는 '로그아웃'.
 * 토큰이 바뀌면(로그인, 로그아웃, 401 AUTH_REQUIRED) 바로 다시 그린다.
 */
export function Header() {
  const token = useAccessToken();
  const location = useLocation();
  const navigate = useNavigate();

  // '로그인'으로 로그인하면 보던 화면으로 돌아온다(결정 0061). 이미 로그인 화면이면 가드나 AUTH_REQUIRED 처리가 넘긴
  // 돌아갈 곳을 그대로 넘겨, 눌러도 잃지 않게 한다. 로그인 화면인지는 라우터와 같은 규칙으로 판단한다(결정 0043).
  const onLoginPage = matchPath(LOGIN_PATH, location.pathname) !== null;
  const loginState: unknown = onLoginPage ? location.state : loginRedirectState(location);

  // 로그아웃: 서버 API 없이 토큰을 지우고 공연 목록으로 간다. 안내 문구는 보여 주지 않는다(결정 0060, 0061).
  function logout() {
    clearAccessToken();
    void navigate(HOME_PATH);
  }

  return (
    <header className={styles.header}>
      <nav className={styles.nav} aria-label="주 메뉴">
        <Link className={styles.brand} to={HOME_PATH}>
          커튼콜
        </Link>
        <NavLink to={RESERVATIONS_PATH}>내 예매</NavLink>
        {token === null ? (
          <NavLink to={LOGIN_PATH} state={loginState}>
            로그인
          </NavLink>
        ) : (
          <button type="button" onClick={logout}>
            로그아웃
          </button>
        )}
      </nav>
    </header>
  );
}
