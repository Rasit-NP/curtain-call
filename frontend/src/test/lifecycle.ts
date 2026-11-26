import { cleanup } from '@testing-library/react';
import { vi } from 'vitest';
import { server, settleRequests, takeUnhandledRequests } from './server';

function assertNoUnhandledRequests(): void {
  const unhandled = takeUnhandledRequests();
  if (unhandled.length > 0) {
    throw new Error(`핸들러가 없는 요청이 있었습니다:\n${unhandled.join('\n')}`);
  }
}

/**
 * 테스트마다 끝에 실행한다(setup.ts의 afterEach).
 * 화면을 정리(cleanup)하면서 보내는 요청(예: 화면을 떠날 때의 DELETE)과 테스트 끝에 처리 중인 요청을
 * 이 테스트의 핸들러로 처리하고 검사하도록, 핸들러를 초기화하기 전에 요청이 끝나기를 기다린다.
 */
export async function finishTest(): Promise<void> {
  vi.useRealTimers();
  cleanup();
  await settleRequests();
  server.resetHandlers();
  localStorage.clear();
  assertNoUnhandledRequests();
}

/** 테스트 파일 끝에 실행한다(setup.ts의 afterAll). 마지막 테스트가 끝난 뒤에 나간 요청도 검사한다. */
export async function finishFile(): Promise<void> {
  await settleRequests();
  assertNoUnhandledRequests();
}
