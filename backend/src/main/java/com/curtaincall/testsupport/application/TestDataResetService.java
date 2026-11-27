package com.curtaincall.testsupport.application;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.testsupport.infra.SeedScripts;

/**
 * E2E 데이터 초기화(spec 6.9). e2e 프로필에서만 등록한다. 데이터를 비우고 시드를 다시 넣는다.
 *
 * <ul>
 *   <li>Flyway 이력 테이블을 뺀 모든 테이블을 {@code TRUNCATE ... RESTART IDENTITY CASCADE}로 비운다. 선점·예매·결제·멱등 기록뿐 아니라
 *       시드가 넣는 계정·공연장·공연·좌석도 비워, 바뀐 시드 값과 더해진 행이 남지 않게 한다. 테이블 목록은 DB에서 읽어 새 테이블도 빠지지 않는다.</li>
 *   <li>시드 파일을 버전 순서대로 다시 실행한다({@link SeedScripts}). Flyway 이력은 그대로 둔다(시드는 이미 적용된 것으로 남는다).</li>
 *   <li>한 트랜잭션에서 한다. TRUNCATE가 테이블을 배타적으로 잠그므로 다른 요청은 비운 상태를 보지 못하고 초기화가 끝나기를 기다린다.
 *       시드가 실패하면 비운 것도 되돌려진다.</li>
 * </ul>
 *
 * <p>마이그레이션이 넣는 기준 데이터(시드가 아닌 데이터)가 생기면 비우는 범위를 다시 정한다(결정 0160).
 */
@Profile("e2e")
@Service
public class TestDataResetService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final String flywayHistoryTable;

    public TestDataResetService(JdbcTemplate jdbcTemplate, DataSource dataSource, Flyway flyway) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.flywayHistoryTable = flyway.getConfiguration().getTable();
    }

    @Transactional
    public void reset() {
        truncateAllTablesExceptFlywayHistory();
        SeedScripts.apply(dataSource);
    }

    private void truncateAllTablesExceptFlywayHistory() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename <> ? ORDER BY tablename",
                String.class, flywayHistoryTable);
        if (tables.isEmpty()) {
            return;
        }
        String names = tables.stream()
                .map(name -> '"' + name.replace("\"", "\"\"") + '"')
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + names + " RESTART IDENTITY CASCADE");
    }
}
