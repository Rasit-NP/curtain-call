/**
 * 라우터 이동 밖에서 화면을 떠날 때(로그아웃) 지금 화면에 먼저 물어보는 장치.
 *
 * 라우터 이동(링크, 뒤로가기)은 화면이 useBlocker로 막고 판단한다. 로그아웃은 이동하기 전에 토큰을 지우므로
 * useBlocker만으로는 토큰이 지워진 뒤에야 알게 된다. 결제 화면은 토큰을 지우기 전에 선점을 해제해야 하고(spec 7.3·7.8),
 * 결제 요청 중에는 로그아웃도 막아야 해서(결정 0131) 로그아웃이 이 장치로 먼저 묻는다.
 */

/** 떠나도 되면 true, 지금은 떠나면 안 되면 false를 돌려준다. 떠나기 전에 할 일(선점 해제)을 마친 뒤에 끝난다. */
export type LeaveGuard = () => Promise<boolean>;

const guards = new Set<LeaveGuard>();

/** 지금 화면의 확인 함수를 등록하고, 등록을 끊는 함수를 돌려준다. 화면이 effect에서 등록하고 정리 함수에서 끊는다. */
export function registerLeaveGuard(guard: LeaveGuard): () => void {
  guards.add(guard);
  return () => {
    guards.delete(guard);
  };
}

/** 등록된 화면에 차례로 물어, 모두 떠나도 된다고 하면 true다. 등록된 화면이 없으면 true다. */
export async function confirmLeave(): Promise<boolean> {
  for (const guard of [...guards]) {
    if (!(await guard())) return false;
  }
  return true;
}
