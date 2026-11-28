package com.curtaincall.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트가 공유하는 PostgreSQL 17 컨테이너({@link IntegrationTest#POSTGRES})에 빈 DB를 하나 더 만든다.
 *
 * <p>test 프로필 컨텍스트가 쓰는 DB는 Flyway 이력이 test 프로필의 위치(스키마만)로 남아 있고, 테스트마다 테이블을 비운다.
 * 운영(prod) 프로필처럼 다른 Flyway 위치로 처음부터 마이그레이션하는 테스트는 그 DB를 같이 쓸 수 없으므로 이것으로 새 DB를 받는다.
 * 컨테이너를 하나 더 띄우지 않는다. DB는 JVM이 끝나 컨테이너가 지워질 때 함께 없어진다.
 */
public final class FreshDatabases {

    /** 새 DB의 접속 정보. 운영처럼 환경변수(DB_URL 등)로 넘길 값이다. */
    public record Database(String jdbcUrl, String username, String password) {
    }

    private FreshDatabases() {
    }

    /** 이름이 {@code prefix_<임의 문자열>}인 빈 DB를 만든다. */
    public static Database create(String prefix) {
        PostgreSQLContainer postgres = IntegrationTest.POSTGRES;
        String name = (prefix + "_" + UUID.randomUUID().toString().replace("-", "")).toLowerCase(Locale.ROOT);
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + name + "\"");
        } catch (SQLException e) {
            throw new IllegalStateException("테스트용 DB를 만들지 못했다: " + name, e);
        }
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + name;
        return new Database(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }
}
