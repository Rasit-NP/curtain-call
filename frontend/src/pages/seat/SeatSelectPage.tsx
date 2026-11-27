import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { errorMessage, isShownOnScreen } from '../../api/errorMessages';
import { ApiError, type ApiErrorCode } from '../../api/errors';
import { createHold, releaseHoldOnLeave } from '../../api/holds';
import { getPerformance } from '../../api/performances';
import type { PerformanceDetail, Seat } from '../../api/types';
import { LoadStatus } from '../../components/LoadStatus';
import { useLoad } from '../../components/useLoad';
import { formatDateTime, formatPrice, formatSeats, seatName } from '../../format';
import { FROM_SEAT_SELECT_STATE, holdPath, HOME_PATH } from '../../paths';
import styles from './SeatSelectPage.module.css';

/** 한 번에 고를 수 있는 좌석 수(R2). 화면은 이번에 고른 좌석만 센다. 이미 가진 좌석까지 합친 한도는 서버가 막는다(spec 7.2). */
const MAX_SEATS = 4;

/** 격자 한 칸의 크기 */
const CELL_SIZE = '2.75rem';

/**
 * 좌석 선택 화면 라우트(/performances/:performanceId, 로그인 필요, spec 7.2). 가드(RequireAuth) 아래에 둔다(R6).
 * 주소의 공연이 바뀌면 고른 좌석과 안내 문구가 남지 않도록 공연마다 화면을 새로 만든다.
 */
export function SeatSelectRoute() {
  const { performanceId = '' } = useParams();
  return <SeatSelectPage key={performanceId} performanceId={performanceId} />;
}

/** 화면 아래의 안내 문구(spec 7.6). codes를 차례로 보여 준다. id가 바뀌면 같은 문구라도 새로 그려 화면 읽기 프로그램이 다시 읽는다. */
interface Notice {
  id: number;
  codes: ApiErrorCode[];
}

/**
 * 좌석을 1~4석 고르고 '좌석 선택 완료'로 한 번에 선점한다(spec 1·7.2, 결정 0080·0081).
 * - 고르는 동안에는 서버에 아무것도 잡지 않는다. 공연 상세는 공개 API라 토큰을 붙이지 않고, 선점 요청에는 붙인다(R6).
 * - 선점 요청은 멱등하지 않아 자동으로 다시 보내지 않는다. 요청과 그 뒤 다시 불러오기가 끝날 때까지 좌석과 완료 버튼을 막는다.
 * - 401 AUTH_REQUIRED는 따로 처리하지 않고 공통 처리에 맡긴다(결정 0043).
 */
function SeatSelectPage({ performanceId }: { performanceId: string }) {
  const titleId = useId();
  const navigate = useNavigate();
  const load = useCallback(() => getPerformance(performanceId), [performanceId]);
  const state = useLoad(load);
  // 409 SEAT_ALREADY_HELD 뒤 다시 불러온 공연 상세. 있으면 처음 불러온 것 대신 쓴다.
  const [reloaded, setReloaded] = useState<PerformanceDetail | null>(null);
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<number>>(() => new Set());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const noticeCount = useRef(0);
  // 버튼이 막히기 전(다시 그리기 전)에 들어온 두 번째 클릭을 막는다.
  const inFlight = useRef(false);
  const mounted = useRef(false);
  const [, setFatal] = useState<null>(null);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  /** ApiError가 아닌 예외는 코드 실수이므로 삼키지 않고 라우터의 오류 화면으로 넘긴다(useLoad와 같다). */
  function crash(error: unknown) {
    setFatal(() => {
      throw error;
    });
  }

  function showNotice(codes: ApiErrorCode[]) {
    // AUTH_REQUIRED는 로그인 화면만 문구를 보여 준다(결정 0132).
    const shown = codes.filter(isShownOnScreen);
    if (shown.length === 0) return;
    noticeCount.current += 1;
    setNotice({ id: noticeCount.current, codes: shown });
  }

  function toggle(seat: Seat) {
    if (selectedIds.has(seat.id)) {
      setNotice(null);
      setSelectedIds(removeId(selectedIds, seat.id));
    } else if (selectedIds.size >= MAX_SEATS) {
      // 5번째 좌석은 고르지 않고 안내만 한다(R2).
      showNotice(['HOLD_LIMIT_EXCEEDED']);
    } else {
      setNotice(null);
      setSelectedIds(addId(selectedIds, seat.id));
    }
  }

  async function reloadSeats() {
    try {
      const fresh = await getPerformance(performanceId);
      if (mounted.current) setReloaded(fresh);
    } catch (error) {
      if (!(error instanceof ApiError)) {
        crash(error);
        return;
      }
      // 격자는 그대로 두고, 좌석이 잡혔다는 문구 아래에 다시 불러오지 못한 이유를 함께 보여 준다.
      if (mounted.current) showNotice(['SEAT_ALREADY_HELD', error.code]);
    }
  }

  async function submit(performance: PerformanceDetail, seatIds: number[]) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setNotice(null);
    try {
      const hold = await createHold({ performanceId: performance.id, seatIds });
      if (mounted.current) {
        // 결제 화면으로 일반 이동(push)한다. 이 화면은 사라지므로 막아 둔 채로 둔다.
        // 결제 화면의 '뒤로 가기'가 기록을 되돌려도 되는지 알 수 있게 좌석 선택 화면에서 왔다고 표시한다.
        await navigate(holdPath(hold.id), { state: FROM_SEAT_SELECT_STATE });
      } else {
        // 요청 중에 이 화면을 떠났다. 결제 화면으로 끌고 가지 않고, 결제 전에 떠난 것이므로 선점을 해제한다(spec 1·7.2, 결정 0132).
        await releaseHoldOnLeave(hold.id);
      }
      return;
    } catch (error) {
      if (!(error instanceof ApiError)) {
        crash(error);
        return;
      }
      if (!mounted.current) return;
      if (error.code === 'SEAT_ALREADY_HELD') {
        // 고른 좌석을 모두 비우고, 다시 불러온 좌석 상태로 잡힌 좌석을 '선택 불가'로 보여 준다(R1).
        setSelectedIds(new Set());
        showNotice(['SEAT_ALREADY_HELD']);
        await reloadSeats();
      } else {
        // 한도 초과(R2), 404, 그 밖의 오류와 응답 없음: 고른 좌석은 그대로 두고 문구만 보여 준다.
        showNotice([error.code]);
      }
    }
    inFlight.current = false;
    if (mounted.current) setBusy(false);
  }

  const performance = reloaded ?? (state.status === 'success' ? state.data : null);

  return (
    <section className={styles.page} aria-labelledby={titleId}>
      <h1 id={titleId}>좌석 선택</h1>
      <LoadStatus state={state} />
      {state.status === 'error' && state.code === 'NOT_FOUND' && <Link to={HOME_PATH}>공연 목록</Link>}
      {performance !== null && (
        <>
          <div className={styles.info}>
            <h2 className={styles.title}>{performance.title}</h2>
            <p className={styles.detail}>
              {formatDateTime(performance.startsAt)} · {performance.venue}
            </p>
            <p className={styles.detail}>1석 {formatPrice(performance.price)}</p>
          </div>
          {performance.seats.length === 0 ? (
            <p role="status">좌석 정보가 없습니다.</p>
          ) : (
            <SeatSelection
              performance={performance}
              selectedIds={selectedIds}
              busy={busy}
              notice={notice}
              onToggle={toggle}
              onSubmit={(seatIds) => void submit(performance, seatIds)}
            />
          )}
        </>
      )}
    </section>
  );
}

interface SeatSelectionProps {
  performance: PerformanceDetail;
  selectedIds: ReadonlySet<number>;
  busy: boolean;
  notice: Notice | null;
  onToggle: (seat: Seat) => void;
  onSubmit: (seatIds: number[]) => void;
}

/** 무대, 좌석 격자, 범례, 고른 좌석과 합계, 안내 문구, 완료 버튼 */
function SeatSelection({ performance, selectedIds, busy, notice, onToggle, onSubmit }: SeatSelectionProps) {
  const { seats } = performance;
  // 좌석 목록은 row, number 오름차순(spec 5)이라, 고른 좌석도 고른 순서가 아니라 좌석 순서가 된다.
  const selectedSeats = seats.filter((seat) => isSelectable(seat) && selectedIds.has(seat.id));
  // 격자 크기는 좌석 위치의 최댓값이다. 1번째 줄이 무대 쪽이다(spec 2.1, 결정 0051).
  const rows = Math.max(...seats.map((seat) => seat.gridRow));
  const columns = Math.max(...seats.map((seat) => seat.gridColumn));

  return (
    <>
      <div className={styles.hall}>
        <div className={styles.hallInner}>
          <div className={styles.stage}>무대</div>
          <div
            role="group"
            aria-label="좌석"
            className={styles.grid}
            style={{
              gridTemplateRows: `repeat(${rows}, ${CELL_SIZE})`,
              gridTemplateColumns: `repeat(${columns}, ${CELL_SIZE})`,
            }}
          >
            {seats.map((seat) => (
              <SeatButton
                key={seat.id}
                seat={seat}
                selected={selectedIds.has(seat.id)}
                busy={busy}
                onToggle={() => onToggle(seat)}
              />
            ))}
          </div>
        </div>
      </div>
      <ul className={styles.legend} aria-label="좌석 상태">
        <li>
          <span className={`${styles.swatch} ${styles.available}`} aria-hidden="true" />
          선택 가능
        </li>
        <li>
          <span className={`${styles.swatch} ${styles.unavailable}`} aria-hidden="true" />
          선택 불가
        </li>
      </ul>
      <div className={styles.summary}>
        <p>선택한 좌석 {selectedSeats.length === 0 ? '없음' : formatSeats(selectedSeats)}</p>
        <p>합계 {formatPrice(performance.price * selectedSeats.length)}</p>
      </div>
      {notice !== null && (
        <div key={notice.id} role="alert" className={styles.alert}>
          {notice.codes.map((code, index) => (
            <p key={index}>{errorMessage(code)}</p>
          ))}
        </div>
      )}
      {notice?.codes.includes('NOT_FOUND') && <Link to={HOME_PATH}>공연 목록</Link>}
      <div>
        <button
          type="button"
          className={styles.submit}
          disabled={busy || selectedSeats.length === 0}
          onClick={() => onSubmit(selectedSeats.map((seat) => seat.id))}
        >
          좌석 선택 완료
        </button>
      </div>
    </>
  );
}

interface SeatButtonProps {
  seat: Seat;
  selected: boolean;
  busy: boolean;
  onToggle: () => void;
}

/**
 * 좌석 버튼. 접근성 이름은 '{표시 이름} {상태}'이고(spec 7.2), 선점됨·판매됨은 같은 '선택 불가'로 보이며 누를 수 없다(R1).
 * 격자 위치는 gridRow·gridColumn을 grid-row·grid-column에 그대로 넣는다.
 */
function SeatButton({ seat, selected, busy, onToggle }: SeatButtonProps) {
  const selectable = isSelectable(seat);
  const label = !selectable ? '선택 불가' : selected ? '선택됨' : '선택 가능';
  const look = !selectable ? styles.unavailable : selected ? styles.selected : styles.available;
  return (
    <button
      type="button"
      className={`${styles.seat} ${look}`}
      aria-label={`${seatName(seat)} ${label}`}
      disabled={!selectable || busy}
      onClick={onToggle}
      style={{ gridRow: seat.gridRow, gridColumn: seat.gridColumn }}
    >
      {seatName(seat)}
    </button>
  );
}

function isSelectable(seat: Seat): boolean {
  return seat.status === 'AVAILABLE';
}

function addId(ids: ReadonlySet<number>, id: number): ReadonlySet<number> {
  return new Set(ids).add(id);
}

function removeId(ids: ReadonlySet<number>, id: number): ReadonlySet<number> {
  const next = new Set(ids);
  next.delete(id);
  return next;
}
