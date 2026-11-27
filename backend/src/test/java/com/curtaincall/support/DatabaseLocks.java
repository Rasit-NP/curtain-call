package com.curtaincall.support;

import java.time.Duration;
import java.util.concurrent.Future;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 동시성 테스트가 "요청이 DB 행 잠금을 기다리는 중"인지 PostgreSQL에 직접 묻는다(pg_stat_activity의 wait_event_type = 'Lock').
 *
 * <p>모의 결제를 멈춰 결제가 잠금을 쥔 채 머물게 한 뒤 다른 요청을 보내고, 그 요청이 잠금을 기다리기 시작한 것을 확인한 다음 결제를 풀어 준다.
 * 이렇게 하면 "나중 요청이 결제가 끝나기를 기다린다"는 순서를 시간(sleep)에 기대지 않고 만든다. 구현의 잠금을 되돌리면 나중 요청은 기다리지 않고
 * 먼저 끝나므로, 기다리는 것과 끝난 것 중 먼저 일어난 쪽을 돌려준다.
 */
public class DatabaseLocks {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseLocks(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 나중 요청의 상태: 행 잠금을 기다리는 중이거나, 기다리지 않고 이미 끝났다. */
    public enum Observed {
        WAITING_FOR_LOCK,
        FINISHED
    }

    /**
     * 이 데이터베이스에서 행 잠금을 기다리는 연결이 waiters개 이상이 되거나, requests 중 하나가 끝날 때까지 기다린다.
     *
     * @throws IllegalStateException timeout 안에 둘 다 일어나지 않으면
     */
    public Observed awaitLockWaitersOrDone(int waiters, Duration timeout, Future<?>... requests) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (lockWaiters() >= waiters) {
                return Observed.WAITING_FOR_LOCK;
            }
            for (Future<?> request : requests) {
                if (request.isDone()) {
                    return Observed.FINISHED;
                }
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        throw new IllegalStateException("요청이 " + timeout + " 안에 잠금을 기다리지도 끝나지도 않았다");
    }

    /** 지금 행 잠금을 기다리는 연결 수(이 연결 제외). */
    public int lockWaiters() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'""", Integer.class);
        return count == null ? 0 : count;
    }
}
