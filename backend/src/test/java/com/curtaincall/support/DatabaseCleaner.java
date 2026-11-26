package com.curtaincall.support;

import java.util.List;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트마다 시작 전에 Flyway 이력 테이블을 뺀 모든 테이블을 비운다.
 * 테스트가 실제로 커밋하는 동작을 그대로 검증하도록 @Transactional 롤백 대신 이 방식을 쓴다.
 */
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;
    private final String flywayHistoryTable;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate, Flyway flyway) {
        this.jdbcTemplate = jdbcTemplate;
        this.flywayHistoryTable = flyway.getConfiguration().getTable();
    }

    public String flywayHistoryTable() {
        return flywayHistoryTable;
    }

    /** 현재 스키마의 테이블 중 Flyway 이력 테이블을 뺀 것. */
    public List<String> tablesToTruncate() {
        return jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename <> ? ORDER BY tablename",
                String.class, flywayHistoryTable);
    }

    public void truncateAllTables() {
        List<String> tables = tablesToTruncate();
        if (tables.isEmpty()) {
            return;
        }
        String names = tables.stream()
                .map(name -> '"' + name.replace("\"", "\"\"") + '"')
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + names + " RESTART IDENTITY CASCADE");
    }
}
