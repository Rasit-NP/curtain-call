import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Link, useBlocker, useLocation, useNavigate, useParams, type BlockerFunction } from 'react-router';
import { errorMessage, isShownOnScreen } from '../../api/errorMessages';
import { ApiError, NO_RESPONSE, type ApiErrorCode } from '../../api/errors';
import { getHold, releaseHoldOnLeave } from '../../api/holds';
import { createPayment } from '../../api/payments';
import type { Hold, PaymentResult, PerformanceSummary, SeatRef } from '../../api/types';
import { getAccessToken } from '../../auth/tokenStorage';
import { LoadStatus } from '../../components/LoadStatus';
import { useLoad } from '../../components/useLoad';
import { formatDateTime, formatPrice, formatSeats } from '../../format';
import { registerLeaveGuard } from '../../navigation/leaveGuard';
import { isFromSeatSelect, performancePath, RESERVATIONS_PATH } from '../../paths';
import styles from './PaymentPage.module.css';

/**
 * 결제·예매 확인 화면 라우트(/holds/:holdId, 로그인 필요, spec 7.3). 가드(RequireAuth) 아래에 둔다(R6).
 * 주소의 선점이 바뀌면 결제 상태와 멱등 키가 남지 않도록 선점마다 화면을 새로 만든다.
 */
export function PaymentRoute() {
  const { holdId = '' } = useParams();
  return <PaymentPage key={holdId} holdId={holdId} />;
}

/** 화면의 안내 문구(spec 7.6). id가 바뀌면 같은 문구라도 새로 그려 화면 읽기 프로그램이 다시 읽는다. */
interface Notice {
  id: number;
  code: ApiErrorCode;
}

/**
 * 선점을 불러와 보여 주고 '결제하기'로 결제한다(spec 1·7.3, 결정 0130·0131).
 * - 남은 시간은 보여 주지 않고 시간을 세지 않는다. 만료는 서버만 판단한다(R3): 선점이 EXPIRED로 오거나 결제가 409 HOLD_EXPIRED면
 *   만료 안내를 보여 주고 '결제하기'를 남긴 채 막는다(R4).
 * - 멱등 키는 응답을 받으면 다음에 새로 만들고, 받지 못하면(NO_RESPONSE) 다음에 다시 쓴다(R5). 요청 중에는 버튼을 막는다.
 * - 결제가 승인되기 전에 앱 안의 다른 화면으로 가면 선점을 해제한 뒤 이동한다. 떠나는지는 라우터 이동(useBlocker)과
 *   로그아웃 확인(registerLeaveGuard)으로 판단한다. useEffect 정리 함수로 판단하면 StrictMode에서 들어오자마자 해제된다.
 * - 401 AUTH_REQUIRED는 따로 처리하지 않고 공통 처리에 맡긴다(결정 0043). 그때는 토큰이 지워져 해제하지 않는다.
 */
function PaymentPage({ holdId }: { holdId: string }) {
  const titleId = useId();
  const navigate = useNavigate();
  const location = useLocation();
  const load = useCallback(() => getHold(holdId), [holdId]);
  const state = useLoad(load);
  const [paying, setPaying] = useState(false);
  const [leaving, setLeaving] = useState(false);
  // 결제 응답으로 알게 된 상태: 만료(409 HOLD_EXPIRED), 선점 없음(404 NOT_FOUND)
  const [expiredByPayment, setExpiredByPayment] = useState(false);
  const [gone, setGone] = useState(false);
  const [paid, setPaid] = useState<PaymentResult | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const noticeCount = useRef(0);
  // 렌더링 밖(이동 확인, 비동기 응답)에서 읽는 상태. 버튼이 막히기 전(다시 그리기 전)에 들어온 두 번째 클릭도 막는다.
  const payingRef = useRef(false);
  const leavingRef = useRef(false);
  // 결제가 승인됐거나 해제를 마쳤다. 그 뒤의 이동은 막지 않는다.
  const settledRef = useRef(false);
  // 응답을 받지 못한 결제의 멱등 키. 다음에 누르면 다시 쓴다(R5).
  const pendingKey = useRef<string | null>(null);
  const [, setFatal] = useState<null>(null);

  /** ApiError가 아닌 예외는 코드 실수이므로 삼키지 않고 라우터의 오류 화면으로 넘긴다(useLoad와 같다). */
  function crash(error: unknown) {
    setFatal(() => {
      throw error;
    });
  }

  function showNotice(code: ApiErrorCode) {
    // AUTH_REQUIRED는 로그인 화면만 문구를 보여 준다(결정 0132).
    if (!isShownOnScreen(code)) return;
    noticeCount.current += 1;
    setNotice({ id: noticeCount.current, code });
  }

  /**
   * 화면을 떠나기 전에 부른다. 떠나도 되면 true, 지금은 떠나면 안 되면 false다(결정 0131).
   * - 결제가 승인됐거나 해제를 마쳤으면, 또는 저장된 토큰이 없으면(AUTH_REQUIRED, 로그아웃 뒤) 해제 없이 떠난다.
   * - 결제 요청 중이거나 이미 해제 중이면 떠나지 않는다. 결제와 해제가 경쟁하지 않고 사용자가 결과를 보고 떠나게 한다.
   * - 그 밖에는 해제를 보내고 응답(또는 실패)을 받은 뒤 떠난다. 그동안 '결제하기'를 막는다.
   */
  const releaseBeforeLeaving = useCallback(async (): Promise<boolean> => {
    if (settledRef.current || getAccessToken() === null) return true;
    if (payingRef.current || leavingRef.current) return false;
    leavingRef.current = true;
    setLeaving(true);
    await releaseHoldOnLeave(holdId);
    settledRef.current = true;
    return true;
  }, [holdId]);

  // 라우터 이동(뒤로가기, 링크, 헤더 메뉴, '뒤로 가기')은 막아 두고 releaseBeforeLeaving으로 판단한다.
  // 같은 화면 안의 이동(경로가 같음)과 해제가 필요 없는 이동은 막지 않는다.
  const shouldBlock = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) =>
      currentLocation.pathname !== nextLocation.pathname && !settledRef.current && getAccessToken() !== null,
    [],
  );
  const blocker = useBlocker(shouldBlock);
  const blockerRef = useRef(blocker);
  const handlingBlock = useRef(false);

  useEffect(() => {
    blockerRef.current = blocker;
  });

  useEffect(() => {
    if (blocker.state !== 'blocked' || handlingBlock.current) return;
    handlingBlock.current = true;
    void releaseBeforeLeaving().then((ok) => {
      handlingBlock.current = false;
      // 기다리는 동안 다른 이동(예: AUTH_REQUIRED 공통 처리)이 이 막음을 풀었을 수 있으므로 지금 막음을 다시 본다.
      const current = blockerRef.current;
      if (current.state !== 'blocked') return;
      if (ok) current.proceed();
      else current.reset();
    });
  }, [blocker, releaseBeforeLeaving]);

  // 로그아웃은 토큰을 지운 뒤 이동하므로, 토큰을 지우기 전에 이 화면에 먼저 묻는다(spec 7.8).
  useEffect(() => registerLeaveGuard(releaseBeforeLeaving), [releaseBeforeLeaving]);

  async function pay(hold: Hold) {
    if (payingRef.current || leavingRef.current || settledRef.current) return;
    payingRef.current = true;
    setPaying(true);
    setNotice(null);
    const key = pendingKey.current ?? crypto.randomUUID();
    pendingKey.current = key;
    try {
      const result = await createPayment(hold.id, key);
      pendingKey.current = null;
      settledRef.current = true;
      setPaid(result);
    } catch (error) {
      if (!(error instanceof ApiError)) {
        crash(error);
        return;
      }
      // 응답을 받았으면 상태 코드와 상관없이 다음에는 새 키를 만든다. 받지 못했으면 결과를 모르므로 같은 키를 다시 쓴다(R5).
      if (error.code !== NO_RESPONSE) pendingKey.current = null;
      if (error.code === 'HOLD_EXPIRED') setExpiredByPayment(true);
      if (error.code === 'NOT_FOUND') setGone(true);
      showNotice(error.code);
    } finally {
      payingRef.current = false;
      setPaying(false);
    }
  }

  /**
   * '뒤로 가기': 그 공연의 좌석 선택 화면으로 돌아간다(결정 0130). 좌석 선택 화면에서 왔으면 브라우저 뒤로가기처럼 기록을 되돌리고,
   * 아니면(주소로 바로 들어옴, 로그인 뒤 돌아옴) 지금 기록을 좌석 선택 화면으로 바꾼다. 어느 쪽이든 라우터 이동이라 해제를 거친다.
   */
  function goBack(hold: Hold) {
    if (isFromSeatSelect(location.state)) void navigate(-1);
    else void navigate(performancePath(hold.performance.id), { replace: true });
  }

  if (paid !== null) {
    const { reservation } = paid;
    return (
      <section className={styles.page} aria-labelledby={titleId}>
        <h1 id={titleId}>예매 완료</h1>
        <p role="status" className={styles.done}>
          예매가 완료되었습니다.
        </p>
        <Booking performance={reservation.performance} seats={reservation.seats} amount={reservation.amount} />
        <Link to={RESERVATIONS_PATH}>내 예매</Link>
      </section>
    );
  }

  const hold = state.status === 'success' ? state.data : null;
  const expired = hold?.status === 'EXPIRED' || expiredByPayment;
  // 들어올 때 이미 만료됐으면 처음부터 만료 안내를 보여 준다(R3).
  const shownNotice: Notice | null = notice ?? (hold?.status === 'EXPIRED' ? { id: 0, code: 'HOLD_EXPIRED' } : null);

  return (
    <section className={styles.page} aria-labelledby={titleId}>
      <h1 id={titleId}>결제</h1>
      <LoadStatus state={state} />
      {state.status === 'error' && state.code === 'NOT_FOUND' && <Link to={RESERVATIONS_PATH}>내 예매</Link>}
      {hold !== null && (
        <>
          <Booking performance={hold.performance} seats={hold.seats} amount={hold.amount} />
          <p className={styles.limit}>선택한 좌석은 5분 동안 잡아 둡니다. 5분 안에 결제해 주세요.</p>
          {paying && <p role="status">결제를 처리하는 중입니다.</p>}
          {shownNotice !== null && (
            <p key={shownNotice.id} role="alert" className={styles.alert}>
              {errorMessage(shownNotice.code)}
            </p>
          )}
          {gone && <Link to={RESERVATIONS_PATH}>내 예매</Link>}
          <div className={styles.actions}>
            <button type="button" disabled={paying || leaving} onClick={() => goBack(hold)}>
              뒤로 가기
            </button>
            {!gone && (
              <button
                type="button"
                className={styles.pay}
                disabled={expired || paying || leaving}
                onClick={() => void pay(hold)}
              >
                결제하기
              </button>
            )}
          </div>
        </>
      )}
    </section>
  );
}

interface BookingProps {
  performance: PerformanceSummary;
  seats: SeatRef[];
  amount: number;
}

/** 공연 제목, 일시·장소, 좌석, 금액. 내 예매 화면의 항목과 같은 모양이다(결정 0131). */
function Booking({ performance, seats, amount }: BookingProps) {
  return (
    <div className={styles.booking}>
      <h2 className={styles.title}>{performance.title}</h2>
      <p className={styles.detail}>
        {formatDateTime(performance.startsAt)} · {performance.venue}
      </p>
      <p className={styles.detail}>좌석 {formatSeats(seats)}</p>
      <p className={styles.detail}>금액 {formatPrice(amount)}</p>
    </div>
  );
}
