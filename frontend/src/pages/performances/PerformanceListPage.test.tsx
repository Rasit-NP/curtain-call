import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { PerformanceSummary } from '../../api/types';
import { saveAccessToken } from '../../auth/tokenStorage';
import { aPerformance, aPerformanceDetail } from '../../test/data';
import { performanceDetailHandler, PERFORMANCES_PATH, performancesHandler, problem } from '../../test/handlers';
import { renderApp } from '../../test/renderApp';
import { server } from '../../test/server';

/** 화면 본문(main). 공통 헤더의 메뉴와 섞이지 않게 목록은 여기서 찾는다. */
function page() {
  return within(screen.getByRole('main'));
}

/** 응답을 테스트가 풀 때까지 붙잡아 두는 핸들러. 로딩 상태를 검사한다. */
function heldPerformances(body: PerformanceSummary[]) {
  let release!: () => void;
  const released = new Promise<void>((resolve) => (release = resolve));
  server.use(
    http.get(PERFORMANCES_PATH, async () => {
      await released;
      return HttpResponse.json(body);
    }),
  );
  return release;
}

describe('공연 목록 화면', () => {
  it('공연마다 제목·일시(한국 시간)·장소·가격을 API가 준 순서대로 보여 준다', async () => {
    server.use(
      performancesHandler([
        aPerformance({ id: 1, title: '작은 방의 햄릿', venue: '대학로 커튼홀', startsAt: '2026-12-20T10:00:00Z', price: 30000 }),
        aPerformance({ id: 7, title: '한여름 밤의 꿈', venue: '혜화 소극장', startsAt: '2027-01-15T15:30:00Z', price: 45000 }),
      ]),
    );

    renderApp('/');

    const items = await page().findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
    expect(items[0]).toHaveTextContent('2026. 12. 20.(일) 19:00');
    expect(items[0]).toHaveTextContent('대학로 커튼홀');
    expect(items[0]).toHaveTextContent('30,000원');
    // UTC 15:30은 한국 시간으로 다음 날 00:30이다.
    expect(within(items[1]).getByRole('heading', { name: '한여름 밤의 꿈' })).toBeInTheDocument();
    expect(items[1]).toHaveTextContent('2027. 1. 16.(토) 00:30');
    expect(items[1]).toHaveTextContent('혜화 소극장');
    expect(items[1]).toHaveTextContent('45,000원');
    expect(screen.getByRole('heading', { level: 1, name: '공연 목록' })).toBeInTheDocument();
  });

  it('항목 전체가 좌석 선택 화면(/performances/{id}) 링크이고, 누르면 그 경로로 간다', async () => {
    // 좌석 선택 화면은 로그인이 필요하고(가드), 들어가면 공연 상세를 불러온다.
    saveAccessToken('token-1');
    server.use(
      performancesHandler([aPerformance({ id: 7, title: '한여름 밤의 꿈' })]),
      performanceDetailHandler(aPerformanceDetail({ id: 7, title: '한여름 밤의 꿈' })),
    );
    const { router, user } = renderApp('/');

    const link = await screen.findByRole('link', { name: /한여름 밤의 꿈/ });
    expect(link).toHaveAttribute('href', '/performances/7');
    // 제목·일시·장소·가격이 모두 링크 안에 있다(항목 전체가 링크).
    expect(link).toHaveTextContent('한여름 밤의 꿈');
    expect(link).toHaveTextContent('2026. 12. 20.(일) 19:00');
    expect(link).toHaveTextContent('대학로 커튼홀');
    expect(link).toHaveTextContent('30,000원');

    await user.click(link);

    expect(await screen.findByRole('heading', { level: 1, name: '좌석 선택' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/performances/7');
  });

  it('R6: 로그인하지 않아도 공연 목록을 볼 수 있다', async () => {
    server.use(performancesHandler([aPerformance({ title: '작은 방의 햄릿' })]));

    const { router } = renderApp('/');

    expect(await screen.findByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it('응답을 받기 전에는 "불러오는 중입니다."를 보여 주고, 받으면 지운다', async () => {
    const release = heldPerformances([aPerformance({ title: '작은 방의 햄릿' })]);
    renderApp('/');

    expect(await screen.findByRole('status')).toHaveTextContent('불러오는 중입니다.');
    expect(page().queryByRole('listitem')).not.toBeInTheDocument();

    release();

    expect(await screen.findByRole('heading', { name: '작은 방의 햄릿' })).toBeInTheDocument();
    expect(screen.queryByText('불러오는 중입니다.')).not.toBeInTheDocument();
  });

  it('공연이 없으면 "예정된 공연이 없습니다."를 보여 준다', async () => {
    server.use(performancesHandler([]));

    renderApp('/');

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예정된 공연이 없습니다.'));
    expect(page().queryByRole('list')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it.each([
    ['500 INTERNAL_ERROR', () => problem(500, 'INTERNAL_ERROR'), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['400 INVALID_INPUT', () => problem(400, 'INVALID_INPUT'), '입력한 내용을 확인해 주세요.'],
    ['code 없는 오류 응답', () => new HttpResponse('Internal Server Error', { status: 500 }), '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'],
    ['응답 없음(네트워크 오류)', () => HttpResponse.error(), '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
  ])('%s면 code별 안내 문구를 role="alert"로 보여 주고 목록·빈 목록 문구는 보여 주지 않는다', async (_name, respond, message) => {
    server.use(http.get(PERFORMANCES_PATH, respond));

    renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(page().queryByRole('list')).not.toBeInTheDocument();
  });

  it('R6: 저장된 토큰이 있어도 공연 목록 화면의 요청에는 토큰을 붙이지 않는다', async () => {
    saveAccessToken('stale-token');
    let authorization: string | null = 'not-called';
    server.use(
      http.get(PERFORMANCES_PATH, ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json([]);
      }),
    );

    renderApp('/');

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('예정된 공연이 없습니다.'));
    expect(authorization).toBeNull();
  });
});
