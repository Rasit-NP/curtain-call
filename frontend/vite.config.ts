/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 코드는 /api로 시작하는 상대 경로만 쓰고, 개발 서버가 백엔드로 넘긴다(frontend/CLAUDE.md).
// 넘길 곳은 로컬 개발 백엔드(8080)다. E2E는 자기가 띄운 백엔드로 바꾼다(CURTAINCALL_API_TARGET, 결정 0161·0162).
const apiTarget = process.env.CURTAINCALL_API_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': apiTarget,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // vi.spyOn으로 바꾼 함수를 테스트마다 원래대로 돌린다. 실패한 테스트의 흉내가 다음 테스트로 새지 않게 한다.
    restoreMocks: true,
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/test/**', 'src/main.tsx', '**/*.d.ts', 'src/**/*.test.{ts,tsx}'],
      reporter: ['text', 'html'],
    },
  },
});
