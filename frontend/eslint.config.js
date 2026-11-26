import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// fetch는 src/api/에서만, localStorage는 src/auth/에서만 쓴다(frontend/CLAUDE.md).
// 규칙을 문서로만 두지 않고 린트로 막는다. src/test/는 테스트 사이에 localStorage를 비운다.
const fetchRestriction = {
  name: 'fetch',
  message: 'fetch는 src/api/의 공통 API 클라이언트에서만 부른다.',
};
const localStorageRestriction = {
  name: 'localStorage',
  message: 'localStorage는 src/auth/에서만 다룬다.',
};
const restrictedProperties = (names) =>
  ['window', 'globalThis', 'self'].flatMap((object) =>
    names.map((property) => ({
      object,
      property,
      message: `${property}는 정해진 폴더에서만 다룬다(frontend/CLAUDE.md).`,
    })),
  );

export default defineConfig([
  globalIgnores(['dist', 'coverage']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/api/**', 'src/auth/**', 'src/test/**'],
    rules: {
      'no-restricted-globals': ['error', fetchRestriction, localStorageRestriction],
      'no-restricted-properties': ['error', ...restrictedProperties(['fetch', 'localStorage'])],
    },
  },
  {
    files: ['src/api/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-globals': ['error', localStorageRestriction],
      'no-restricted-properties': ['error', ...restrictedProperties(['localStorage'])],
    },
  },
  {
    files: ['src/auth/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-globals': ['error', fetchRestriction],
      'no-restricted-properties': ['error', ...restrictedProperties(['fetch'])],
    },
  },
]);
