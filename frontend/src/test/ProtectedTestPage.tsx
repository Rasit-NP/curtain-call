import { useState } from 'react';
import { useLocation } from 'react-router';
import { apiRequest } from '../api/client';
import { ApiError } from '../api/errors';

/**
 * 로그인이 필요한 테스트용 화면. 지금 경로(쿼리 포함)를 보여 주고, 인증이 필요한 API를 부르는 버튼을 둔다.
 * 버튼은 화면 Issue의 화면처럼 오류를 받아 안내만 남기고, AUTH_REQUIRED 처리는 공통 처리에 맡긴다.
 */
export function ProtectedTestPage() {
  const location = useLocation();
  const [result, setResult] = useState('');

  async function load(times: number) {
    const results = await Promise.allSettled(
      Array.from({ length: times }, () => apiRequest('/api/reservations', { auth: true })),
    );
    const failed = results.find((r) => r.status === 'rejected');
    setResult(failed ? `실패: ${(failed.reason as ApiError).code}` : '불러옴');
  }

  return (
    <section>
      <h1>보호된 테스트 화면</h1>
      <p>경로: {location.pathname + location.search}</p>
      <button type="button" onClick={() => void load(1)}>
        내 예매 불러오기
      </button>
      <button type="button" onClick={() => void load(2)}>
        내 예매 두 번 불러오기
      </button>
      {result && <p>{result}</p>}
    </section>
  );
}
