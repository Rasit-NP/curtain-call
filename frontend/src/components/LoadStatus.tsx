import { errorMessage } from '../api/errorMessages';
import styles from './LoadStatus.module.css';
import type { LoadState } from './useLoad';

/**
 * 데이터를 불러오는 화면의 로딩·오류 문구(spec 7.5, 7.6).
 * 로딩은 role="status", 오류는 code별 안내 문구를 role="alert"로 보여 준다(결정 0062). 받은 뒤에는 아무것도 그리지 않는다.
 */
export function LoadStatus({ state }: { state: LoadState<unknown> }) {
  if (state.status === 'loading') return <p role="status">불러오는 중입니다.</p>;
  if (state.status === 'error') {
    return (
      <p role="alert" className={styles.alert}>
        {errorMessage(state.code)}
      </p>
    );
  }
  return null;
}
