import { useId } from 'react';
import { Link } from 'react-router';
import { getPerformances } from '../../api/performances';
import { LoadStatus } from '../../components/LoadStatus';
import { useLoad } from '../../components/useLoad';
import { formatDateTime, formatPrice } from '../../format';
import { performancePath } from '../../paths';
import styles from './PerformanceListPage.module.css';

/**
 * 공연 목록 화면(/, 로그인 필요 없음, spec 7.7). API가 준 순서(시작 시각순)대로 보여 준다.
 * 항목 전체가 좌석 선택 화면 링크다. 공연 목록 API는 공개 API라 토큰을 붙이지 않는다(R6).
 */
export function PerformanceListPage() {
  const titleId = useId();
  const state = useLoad(getPerformances);

  return (
    <section className={styles.page} aria-labelledby={titleId}>
      <h1 id={titleId}>공연 목록</h1>
      <LoadStatus state={state} />
      {state.status === 'success' &&
        (state.data.length === 0 ? (
          <p role="status">예정된 공연이 없습니다.</p>
        ) : (
          <ul className={styles.list}>
            {state.data.map((performance) => (
              <li key={performance.id}>
                <Link className={styles.item} to={performancePath(performance.id)}>
                  <h2 className={styles.title}>{performance.title}</h2>
                  <p className={styles.detail}>{formatDateTime(performance.startsAt)}</p>
                  <p className={styles.detail}>{performance.venue}</p>
                  <p className={styles.detail}>{formatPrice(performance.price)}</p>
                </Link>
              </li>
            ))}
          </ul>
        ))}
    </section>
  );
}
