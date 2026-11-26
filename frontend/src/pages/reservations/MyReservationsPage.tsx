import { useId } from 'react';
import { Link } from 'react-router';
import { getMyReservations } from '../../api/reservations';
import { LoadStatus } from '../../components/LoadStatus';
import { useLoad } from '../../components/useLoad';
import { formatDateTime, formatPrice, formatSeats } from '../../format';
import { HOME_PATH } from '../../paths';
import styles from './MyReservationsPage.module.css';

/**
 * 내 예매 화면(/reservations, 로그인 필요, spec 7.4). 가드(RequireAuth) 아래에 둔다(R6).
 * API가 준 순서(최신순)대로 보여 준다. 401 AUTH_REQUIRED는 따로 처리하지 않고 공통 처리에 맡긴다(결정 0043).
 */
export function MyReservationsPage() {
  const titleId = useId();
  const state = useLoad(getMyReservations);

  return (
    <section className={styles.page} aria-labelledby={titleId}>
      <h1 id={titleId}>내 예매</h1>
      <LoadStatus state={state} />
      {state.status === 'success' &&
        (state.data.length === 0 ? (
          <>
            <p role="status">예매 내역이 없습니다.</p>
            <Link to={HOME_PATH}>공연 목록</Link>
          </>
        ) : (
          <ul className={styles.list}>
            {state.data.map((reservation) => (
              <li key={reservation.id} className={styles.item}>
                <h2 className={styles.title}>{reservation.performance.title}</h2>
                <p className={styles.detail}>
                  {formatDateTime(reservation.performance.startsAt)} · {reservation.performance.venue}
                </p>
                <p className={styles.detail}>좌석 {formatSeats(reservation.seats)}</p>
                <p className={styles.detail}>금액 {formatPrice(reservation.amount)}</p>
              </li>
            ))}
          </ul>
        ))}
    </section>
  );
}
