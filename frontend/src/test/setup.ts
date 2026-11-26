import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { onUnhandledFrame, server, takeUnhandledRequests } from './server';

beforeAll(() => {
  server.listen({ onUnhandledFrame });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  localStorage.clear();
  const unhandled = takeUnhandledRequests();
  if (unhandled.length > 0) {
    throw new Error(`핸들러가 없는 요청이 있었습니다:\n${unhandled.join('\n')}`);
  }
});

afterAll(() => {
  server.close();
});
