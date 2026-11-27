import { execFileSync } from 'node:child_process';
import { defineConfig, devices } from '@playwright/test';

/**
 * E2E 설정(e2e/CLAUDE.md, 결정 0161·0162).
 *
 * E2E는 실행마다 이 체크아웃의 백엔드(e2e 프로필, 임시 DB)와 프론트엔드 개발 서버를 빈 포트에 새로 띄우고, 끝나면 내린다.
 * 사람이 띄운 서버(8080·5173)와 개발 DB(compose db)는 쓰지도 건드리지도 않는다. 이미 떠 있는 서버를 다시 쓰지 않는다.
 */

const BACKEND_PORT = 'E2E_BACKEND_PORT';
const FRONTEND_PORT = 'E2E_FRONTEND_PORT';

/**
 * 127.0.0.1의 빈 포트 둘을 한 번에 고른다(둘이 같지 않게). 고른 포트는 닫았다가 서버가 다시 연다.
 * 설정 파일은 실행기와 작업자 프로세스에서 여러 번 읽힌다. 처음 고른 값을 환경변수에 두어, 나중에 읽는 쪽(작업자는 실행기의 환경을 물려받는다)이
 * 같은 값을 쓰게 한다. 환경변수를 미리 주면 그 포트를 쓴다(디버깅용).
 */
function choosePorts(): { backend: number; frontend: number } {
  if (!process.env[BACKEND_PORT] || !process.env[FRONTEND_PORT]) {
    const script = `
      const net = require('node:net');
      const servers = [net.createServer(), net.createServer()];
      let listening = 0;
      for (const server of servers) {
        server.listen(0, '127.0.0.1', () => {
          if (++listening === servers.length) {
            console.log(servers.map((s) => s.address().port).join(' '));
            for (const s of servers) s.close();
          }
        });
      }`;
    // 하나만 미리 주었으면, 고른 포트 중 그 값과 다른 것을 쓴다(미리 준 포트는 아직 열리지 않아 다시 골라질 수 있다).
    const chosen = execFileSync(process.execPath, ['-e', script], { encoding: 'utf8' }).trim().split(' ');
    process.env[BACKEND_PORT] ||= chosen.find((candidate) => candidate !== process.env[FRONTEND_PORT]);
    process.env[FRONTEND_PORT] ||= chosen.find((candidate) => candidate !== process.env[BACKEND_PORT]);
  }
  return { backend: port(BACKEND_PORT), frontend: port(FRONTEND_PORT) };
}

function port(name: string): number {
  const value = Number(process.env[name]);
  if (!Number.isInteger(value) || value < 1 || value > 65535) {
    throw new Error(`${name}가 포트 번호가 아니다: ${process.env[name]}`);
  }
  return value;
}

const ports = choosePorts();
const backendUrl = `http://127.0.0.1:${ports.backend}`;
const frontendUrl = `http://127.0.0.1:${ports.frontend}`;

export default defineConfig({
  testDir: './tests',
  // 테스트들이 DB 하나를 함께 쓰므로 하나씩 순서대로 실행한다.
  workers: 1,
  fullyParallel: false,
  // .only가 남아 있으면 실패한다(루트 CLAUDE.md '금지 사항').
  forbidOnly: true,
  retries: 0,
  // html 리포터를 그냥 두면 실패했을 때 리포트 서버를 띄우고 기다려 명령이 끝나지 않는다.
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: frontendUrl,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // 백엔드: e2e 프로필, DB는 실행마다 새로 만드는 PostgreSQL 컨테이너(backend의 bootE2eRun). 로그는 test-results/backend.log에 남긴다.
      command:
        `./gradlew bootE2eRun --args='--server.address=127.0.0.1 --server.port=${ports.backend}` +
        ` --logging.file.name=../e2e/test-results/backend.log'`,
      cwd: '../backend',
      url: `${backendUrl}/actuator/health`,
      timeout: 180_000,
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 15_000 },
    },
    {
      // 프론트엔드 개발 서버: /api를 위 백엔드로 넘긴다(frontend/vite.config.ts의 CURTAINCALL_API_TARGET).
      command: `npm run dev -- --host 127.0.0.1 --port ${ports.frontend} --strictPort`,
      cwd: '../frontend',
      url: frontendUrl,
      env: { CURTAINCALL_API_TARGET: backendUrl },
      timeout: 60_000,
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 5_000 },
    },
  ],
});
