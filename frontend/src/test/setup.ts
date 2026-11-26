import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { finishFile, finishTest } from './lifecycle';
import { server, UNHANDLED_REQUEST_STRATEGY } from './server';

beforeAll(() => {
  server.listen({ onUnhandledFrame: UNHANDLED_REQUEST_STRATEGY });
});

afterEach(async () => {
  await finishTest();
});

afterAll(async () => {
  try {
    await finishFile();
  } finally {
    server.close();
  }
});
