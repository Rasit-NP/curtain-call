package com.curtaincall.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트 기반(테스트 시계, 시각 되돌림, 테이블 비우기)이 동작하는지 확인한다.
 * 되돌림·비우기는 같은 테스트를 두 번 반복해, 두 번째 반복이 첫 번째가 남긴 상태를 보지 않는지로 확인한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTestSupportTest extends IntegrationTest {

    private static final String PROBE_TABLE = "integration_support_probe";

    @Autowired
    private Clock injectedClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("테이블을 비울 때 Flyway 이력 테이블은 남긴다")
    void flywayHistoryTableIsKept() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + PROBE_TABLE + " (id BIGSERIAL PRIMARY KEY, name TEXT)");
        jdbcTemplate.update("INSERT INTO " + PROBE_TABLE + " (name) VALUES ('지울 데이터')");
        String history = databaseCleaner.flywayHistoryTable();
        int historyRows = count(history);

        databaseCleaner.truncateAllTables();

        assertThat(databaseCleaner.tablesToTruncate()).contains(PROBE_TABLE).doesNotContain(history);
        assertThat(count(PROBE_TABLE)).isZero();
        assertThat(count(history)).isEqualTo(historyRows);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
