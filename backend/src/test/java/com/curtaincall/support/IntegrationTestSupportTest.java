package com.curtaincall.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.curtaincall.payment.domain.PaymentGateway;

/**
 * 통합 테스트 기반(테스트 시계, 시각 되돌림, 테이블 비우기, 모의 결제 초기화)이 동작하는지 확인한다.
 * 되돌림·비우기는 같은 테스트를 두 번 반복해, 두 번째 반복이 첫 번째가 남긴 상태를 보지 않는지로 확인한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTestSupportTest extends IntegrationTest {

    private static final String PROBE_TABLE = "integration_support_probe";
    private static final int HISTORY_PROBE_RANK = -1;
    private static final PaymentGateway.Request PAYMENT_PROBE = new PaymentGateway.Request(1L, 1L, 30000L);

    @Autowired
    private Clock injectedClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentGateway injectedPaymentGateway;

    @AfterAll
    void dropProbeTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + PROBE_TABLE);
    }

    @Test
    @DisplayName("운영 코드에 주입되는 Clock은 시각을 옮길 수 있는 테스트 시계다")
    void injectedClockIsMutableClock() {
        assertThat(injectedClock).isSameAs(clock);

        Instant at = Instant.parse("2027-01-01T00:00:00Z");
        clock.setInstant(at);
        clock.advance(Duration.ofSeconds(1));

        assertThat(injectedClock.instant()).isEqualTo(at.plusSeconds(1));
    }

    @RepeatedTest(2)
    @DisplayName("테스트마다 시계를 정해진 시각으로 되돌린다")
    void clockIsResetBeforeEachTest() {
        assertThat(injectedClock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT);

        clock.advance(Duration.ofHours(1));
    }

    @RepeatedTest(2)
    @DisplayName("테스트마다 시작 전에 테이블을 비운다")
    void tablesAreTruncatedBeforeEachTest() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + PROBE_TABLE + " (id BIGSERIAL PRIMARY KEY, name TEXT)");
        assertThat(count(PROBE_TABLE)).isZero();

        jdbcTemplate.update("INSERT INTO " + PROBE_TABLE + " (name) VALUES ('남은 데이터')");
        assertThat(count(PROBE_TABLE)).isEqualTo(1);
    }

    @Test
    @DisplayName("운영 코드에 주입되는 PaymentGateway는 테스트가 조작하는 모의 결제와 같은 빈이다")
    void injectedPaymentGatewayIsFake() {
        assertThat(injectedPaymentGateway).isSameAs(paymentGateway);
    }

    @RepeatedTest(2)
    @DisplayName("테스트마다 시작 전에 모의 결제의 설정(다음 호출 거절, 멈추기)과 호출 횟수를 초기화한다")
    void paymentGatewayIsResetBeforeEachTest() throws Exception {
        assertThat(paymentGateway.callCount()).isZero();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 앞 반복이 남긴 멈추기가 남아 있으면 이 호출이 끝나지 않는다.
            Future<PaymentGateway.Result> call = executor.submit(() -> paymentGateway.pay(PAYMENT_PROBE));
            assertThat(call.get(5, TimeUnit.SECONDS)).isEqualTo(PaymentGateway.Result.APPROVED);
        } finally {
            executor.shutdownNow();
        }

        // 다음 반복에 남길 설정
        paymentGateway.declineNext();
        paymentGateway.pauseNext();
    }

    @Test
    @DisplayName("테이블을 비울 때 Flyway 이력 테이블은 남긴다")
    void flywayHistoryTableIsKept() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + PROBE_TABLE + " (id BIGSERIAL PRIMARY KEY, name TEXT)");
        jdbcTemplate.update("INSERT INTO " + PROBE_TABLE + " (name) VALUES ('지울 데이터')");
        String history = databaseCleaner.flywayHistoryTable();
        // 마이그레이션이 아직 없어 이력이 비어 있으므로, 이력이 남는지 볼 표식 행을 넣고 끝나면 지운다.
        jdbcTemplate.update("INSERT INTO " + history
                + " (installed_rank, version, description, type, script, installed_by, execution_time, success)"
                + " VALUES (?, NULL, 'cleaner probe', 'SQL', 'cleaner-probe', 'test', 0, TRUE)", HISTORY_PROBE_RANK);
        try {
            int historyRows = count(history);

            databaseCleaner.truncateAllTables();

            assertThat(databaseCleaner.tablesToTruncate()).contains(PROBE_TABLE).doesNotContain(history);
            assertThat(count(PROBE_TABLE)).isZero();
            assertThat(count(history)).isEqualTo(historyRows).isPositive();
        } finally {
            jdbcTemplate.update("DELETE FROM " + history + " WHERE installed_rank = ?", HISTORY_PROBE_RANK);
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
