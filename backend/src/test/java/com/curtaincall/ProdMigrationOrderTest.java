package com.curtaincall;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.curtaincall.support.FreshDatabases;

/**
 * 배포 서버의 DB는 초기화하지 않고 배포마다 이어서 마이그레이션한다(Issue #71 '이미 정한 정책').
 * 시연 데이터가 들어간 운영 DB에 다음 스키마 마이그레이션(지금 가장 큰 버전 + 1)을 더해도 적용되는지, application.yml의 prod 설정 그대로 확인한다.
 *
 * <p>시연 데이터를 버전 마이그레이션(예: V1000__)으로 넣으면, 그 뒤에 더한 V6 같은 스키마 마이그레이션은 이미 적용된 가장 큰 버전보다 작아
 * out-of-order를 끈 prod에서 검증에 걸리고 애플리케이션이 뜨지 않는다(결정 0013에서 local로 재현한 것과 같은 실패).
 */
@Tag("integration")
class ProdMigrationOrderTest {

    private static final Pattern SCHEMA_MIGRATION = Pattern.compile("V(\\d+)__.+\\.sql");
    private static final String PROBE_TABLE = "later_schema_change_probe";

    @Test
    @DisplayName("시연 데이터가 들어간 운영 DB에도 나중에 더한 스키마 마이그레이션이 적용된다(prod는 out-of-order를 끈 채로)")
    void laterSchemaMigrationAppliesToProdDatabase(@TempDir Path laterMigrations) throws IOException {
        StandardEnvironment prod = prodEnvironment();
        List<String> locations = Binder.get(prod).bind("spring.flyway.locations", Bindable.listOf(String.class)).get();
        boolean outOfOrder = prod.getProperty("spring.flyway.out-of-order", Boolean.class, false);
        FreshDatabases.Database database = FreshDatabases.create("prod_later_migration");

        // 지금 배포: 빈 DB에 스키마와 시연 데이터가 들어간다.
        flyway(database, locations, outOfOrder).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));
        List<String> usersBefore = jdbc.queryForList("SELECT username FROM users ORDER BY username", String.class);
        assertThat(usersBefore).as("시연 계정").isNotEmpty();

        // 다음 배포: 스키마 마이그레이션 하나를 더한다.
        BigInteger next = latestSchemaMigrationVersion().add(BigInteger.ONE);
        Files.writeString(laterMigrations.resolve("V" + next + "__later_schema_change_probe.sql"),
                "CREATE TABLE " + PROBE_TABLE + " (id INTEGER);\n");
        List<String> withLater = new ArrayList<>(locations);
        withLater.add("filesystem:" + laterMigrations.toAbsolutePath());

        MigrateResult result = flyway(database, withLater, outOfOrder).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrations).extracting(migration -> migration.version).containsExactly(next.toString());
        assertThat(jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, PROBE_TABLE)).isEqualTo(PROBE_TABLE);
        assertThat(jdbc.queryForList("SELECT username FROM users ORDER BY username", String.class)).isEqualTo(usersBefore);
    }

    private static Flyway flyway(FreshDatabases.Database database, List<String> locations, boolean outOfOrder) {
        return Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
                .locations(locations.toArray(String[]::new))
                .outOfOrder(outOfOrder)
                .load();
    }

    private static DriverManagerDataSource dataSource(FreshDatabases.Database database) {
        return new DriverManagerDataSource(database.jdbcUrl(), database.username(), database.password());
    }

    /** {@code db/migration}의 스키마 마이그레이션 중 가장 큰 버전. */
    private static BigInteger latestSchemaMigrationVersion() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
        return Arrays.stream(migrations)
                .map(Resource::getFilename)
                .map(SCHEMA_MIGRATION::matcher)
                .filter(Matcher::matches)
                .map(matcher -> new BigInteger(matcher.group(1)))
                .max(BigInteger::compareTo)
                .orElseThrow();
    }

    /** application.yml의 prod 설정. 기계의 환경변수·시스템 속성이 결과를 바꾸지 않게 둘 다 뺀다(ApplicationProfilesTest와 같은 방식). */
    private static StandardEnvironment prodEnvironment() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addLast(new MapPropertySource("fakeEnv", Map.of(
                "DB_URL", "unused", "DB_USERNAME", "unused", "DB_PASSWORD", "unused", "JWT_SECRET", "unused")));
        ConfigDataEnvironmentPostProcessor.applyTo(env, null, null, List.of("prod"));
        return env;
    }
}
