import { useEffect, useState } from 'react';
import { ApiError, type ApiErrorCode } from '../api/errors';

/** 화면이 불러오는 데이터의 상태(spec 7.5): 로딩, 오류(code), 받음 */
export type LoadState<T> =
  | { status: 'loading' }
  | { status: 'error'; code: ApiErrorCode }
  | { status: 'success'; data: T };

/**
 * 화면에 들어올 때 데이터를 한 번 불러온다. load는 바뀌지 않는 함수(예: src/api의 함수)를 넘긴다.
 * - API 오류(응답 없음 포함)는 code로 돌려준다. 401 AUTH_REQUIRED도 code로 올 뿐이고, 로그인 이동은 공통 처리가 한다(결정 0043).
 * - ApiError가 아닌 예외는 코드 실수이므로 삼키지 않고 라우터의 오류 화면으로 넘긴다.
 * - 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useLoad<T>(load: () => Promise<T>): LoadState<T> {
  const [state, setState] = useState<LoadState<T>>({ status: 'loading' });

  useEffect(() => {
    let active = true;
    load().then(
      (data) => {
        if (active) setState({ status: 'success', data });
      },
      (error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError) {
          setState({ status: 'error', code: error.code });
        } else {
          setState(() => {
            throw error;
          });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [load]);

  return state;
}
