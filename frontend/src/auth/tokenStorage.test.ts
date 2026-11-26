import { describe, expect, it } from 'vitest';
import { ACCESS_TOKEN_KEY, clearAccessToken, getAccessToken, saveAccessToken, subscribeAccessToken } from './tokenStorage';

describe('액세스 토큰 저장', () => {
  it('저장한 적이 없으면 null을 돌려준다', () => {
    expect(getAccessToken()).toBeNull();
  });

  it('저장한 토큰을 읽는다', () => {
    saveAccessToken('token-1');

    expect(getAccessToken()).toBe('token-1');
  });

  it('토큰은 localStorage에 저장돼 새로고침 뒤에도 남는다', () => {
    saveAccessToken('token-1');

    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('token-1');
  });

  it('다시 저장하면 새 토큰으로 바뀐다', () => {
    saveAccessToken('token-1');
    saveAccessToken('token-2');

    expect(getAccessToken()).toBe('token-2');
  });

  it('지우면 null을 돌려주고 localStorage에서도 사라진다', () => {
    saveAccessToken('token-1');

    clearAccessToken();

    expect(getAccessToken()).toBeNull();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
  });
});

describe('액세스 토큰 변경 알림', () => {
  it('토큰을 저장하거나 지우면 구독한 함수에 알리고, 구독을 끊으면 알리지 않는다', () => {
    const seen: (string | null)[] = [];
    const unsubscribe = subscribeAccessToken(() => seen.push(getAccessToken()));

    saveAccessToken('token-1');
    clearAccessToken();
    unsubscribe();
    saveAccessToken('token-2');

    expect(seen).toEqual(['token-1', null]);
  });
});
