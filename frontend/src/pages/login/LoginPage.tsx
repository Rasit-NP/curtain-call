import { useId, useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { login } from '../../api/auth';
import { errorMessage } from '../../api/errorMessages';
import { ApiError, type ApiErrorCode } from '../../api/errors';
import { noticeOf, returnPathOf } from '../../auth/loginRedirect';
import { saveAccessToken } from '../../auth/tokenStorage';
import styles from './LoginPage.module.css';

/**
 * 로그인 화면(/login, spec 6.1·7.1).
 * - 성공: 토큰을 저장하고 가드나 AUTH_REQUIRED 처리가 넘긴 원래 화면으로, 없으면 공연 목록(/)으로 간다.
 * - 실패: code별 안내 문구만 보여 주고 머문다. INVALID_CREDENTIALS도 토큰을 지우거나 화면을 옮기지 않는다(spec 3.3, R6).
 */
export function LoginPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const usernameId = useId();
  const passwordId = useId();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  // 처음에는 AUTH_REQUIRED 처리가 넘긴 안내('로그인이 필요합니다.')를 보여 주고, 로그인을 시도하면 지운다.
  const [code, setCode] = useState<ApiErrorCode | null>(() => noticeOf(location.state) ?? null);
  // 버튼이 막히기 전(다시 그리기 전)에 들어온 두 번째 제출을 막는다.
  const inFlight = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    setCode(null);
    try {
      const { accessToken } = await login({ username, password });
      saveAccessToken(accessToken);
      await navigate(returnPathOf(location.state), { replace: true });
    } catch (error) {
      if (!(error instanceof ApiError)) throw error;
      setCode(error.code);
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <section className={styles.page} aria-labelledby={`${usernameId}-title`}>
      <h1 id={`${usernameId}-title`}>로그인</h1>
      <form className={styles.form} onSubmit={(event) => void handleSubmit(event)}>
        <label htmlFor={usernameId}>아이디</label>
        <input
          id={usernameId}
          type="text"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        />
        <label htmlFor={passwordId}>비밀번호</label>
        <input
          id={passwordId}
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        {code !== null && (
          <p role="alert" className={styles.alert}>
            {errorMessage(code)}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          로그인
        </button>
      </form>
    </section>
  );
}
