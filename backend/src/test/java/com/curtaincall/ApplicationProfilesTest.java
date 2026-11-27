package com.curtaincall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * application.yml의 프로필별 설정을 스프링 컨텍스트 없이 읽어 확인한다.
 * 기계의 환경변수·시스템 속성(SPRING_PROFILES_ACTIVE 등)이 결과를 바꾸지 않게 둘 다 뺀다.
 */
class ApplicationProfilesTest {

    private static final String COMPOSE_DB_URL = "jdbc:postgresql://localhost:5432/curtaincall";
    private static final String JWT_SECRET = "curtaincall.jwt.secret";
    private static final List<String> MIGRATION_ONLY = List.of("classpath:db/migration");
    private static final List<String> MIGRATION_AND_SEED = List.of("classpath:db/migration", "classpath:db/seed");

    @Test
    @DisplayName("프로필을 주지 않으면 local로 뜨고 compose DB와 시드를 쓴다")
    void defaultProfileIsLocal() {
        StandardEnvironment env = load(Map.of());

        assertThat(env.getActiveProfiles()).isEmpty();
        assertThat(env.getDefaultProfiles()).containsExactly("local");
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(COMPOSE_DB_URL);
        assertThat(flywayLocations(env)).isEqualTo(MIGRATION_AND_SEED);
    }

    @Test
    @DisplayName("local은 환경변수 없이 compose DB에 붙고 마이그레이션과 시드를 쓴다")
    void localUsesComposeDbAndSeed() {
        StandardEnvironment env = load(Map.of(), "local");

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(COMPOSE_DB_URL);
        assertThat(env.getProperty("spring.datasource.username")).isNotBlank();
        assertThat(env.getProperty("spring.datasource.password")).isNotBlank();
        assertThat(flywayLocations(env)).isEqualTo(MIGRATION_AND_SEED);
    }

    @Test
    @DisplayName("e2e는 마이그레이션과 시드를 쓰지만 DB 주소를 정하지 않는다(E2E 실행기가 실행마다 새 DB를 붙인다. 초기화가 개발 DB를 지우지 않게)")
    void e2eUsesSeedWithoutDbAddress() {
        // 결정 0161: e2e 프로필에서 개발 DB 주소 기본값을 뺀다. E2E 백엔드는 bootE2eRun이 띄운 임시 DB에 붙는다(결정 0162).
        StandardEnvironment env = load(Map.of(), "e2e");

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isNull();
        assertThat(flywayLocations(env)).isEqualTo(MIGRATION_AND_SEED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "e2e"})
    @DisplayName("local과 e2e는 시드(V1000__~)가 적용된 DB에 나중에 추가된 마이그레이션도 적용하도록 out-of-order를 켠다")
    void localAndE2eAllowOutOfOrderMigrations(String profile) {
        // 시드와 스키마 마이그레이션이 같은 이력을 쓰고 compose 볼륨이 이력을 유지한다. 끄면 V1000 뒤에 추가된 V2가
        // "Detected resolved migration not applied to database"로 검증에 걸려 앱이 뜨지 않는다.
        StandardEnvironment env = load(Map.of(), profile);

        assertThat(env.getProperty("spring.flyway.out-of-order")).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "prod"})
    @DisplayName("시드를 쓰지 않는 test와 prod는 마이그레이션 순서를 지킨다(out-of-order 끔)")
    void testAndProdKeepMigrationOrder(String profile) {
        StandardEnvironment env = load(Map.of("DB_URL", "u", "DB_USERNAME", "n", "DB_PASSWORD", "p"), profile);

        assertThat(env.getProperty("spring.flyway.out-of-order", "false")).isEqualTo("false");
    }

    @Test
    @DisplayName("test는 DB 주소를 정하지 않고(Testcontainers가 연결) 시드를 쓰지 않는다")
    void testProfileUsesNoSeed() {
        StandardEnvironment env = load(Map.of(), "test");

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(flywayLocations(env)).isEqualTo(MIGRATION_ONLY);
    }

    @Test
    @DisplayName("prod는 DB 접속 정보를 환경변수로 받고 시드를 쓰지 않는다")
    void prodReadsDbFromEnvironment() {
        StandardEnvironment env = load(Map.of(
                "DB_URL", "jdbc:postgresql://db.example:5432/app",
                "DB_USERNAME", "app-user",
                "DB_PASSWORD", "from-env"), "prod");

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example:5432/app");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("app-user");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("from-env");
        assertThat(flywayLocations(env)).isEqualTo(MIGRATION_ONLY);
    }

    @Test
    @DisplayName("prod는 DB 접속 정보에 기본값이 없어 환경변수가 없으면 값을 정할 수 없다")
    void prodHasNoDbDefaults() {
        StandardEnvironment env = load(Map.of(), "prod");

        for (String key : List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password")) {
            assertThatThrownBy(() -> env.getProperty(key))
                    .as(key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "e2e", "test", "prod"})
    @DisplayName("모든 프로필에서 open-in-view를 끄고 ddl-auto는 validate다")
    void jpaSettingsForEveryProfile(String profile) {
        StandardEnvironment env = load(Map.of("DB_URL", "u", "DB_USERNAME", "n", "DB_PASSWORD", "p"), profile);

        assertThat(env.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "e2e", "test"})
    @DisplayName("local·e2e·test는 저장소에 둔 개발 전용 JWT 서명 키(이름에 dev, 32바이트 이상)를 환경변수 없이 쓴다")
    void devProfilesUseDevJwtKey(String profile) {
        StandardEnvironment env = load(Map.of(), profile);

        String secret = env.getProperty(JWT_SECRET);
        assertThat(secret).contains("dev");
        assertThat(secret.getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("prod는 JWT 서명 키를 환경변수 JWT_SECRET으로만 받는다")
    void prodReadsJwtSecretFromEnvironment() {
        StandardEnvironment env = load(Map.of("JWT_SECRET", "prod-secret-from-environment-0123456789"), "prod");

        assertThat(env.getProperty(JWT_SECRET)).isEqualTo("prod-secret-from-environment-0123456789");
    }

    @Test
    @DisplayName("prod는 JWT 서명 키에 기본값이 없어 JWT_SECRET이 없으면 값을 정할 수 없다(개발 키로 뜨지 않는다)")
    void prodHasNoJwtSecretDefault() {
        StandardEnvironment env = load(Map.of("DB_URL", "u", "DB_USERNAME", "n", "DB_PASSWORD", "p"), "prod");

        assertThatThrownBy(() -> env.getProperty(JWT_SECRET)).isInstanceOf(IllegalArgumentException.class);
    }

    private static StandardEnvironment load(Map<String, Object> envVars, String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addLast(new MapPropertySource("fakeEnv", envVars));
        ConfigDataEnvironmentPostProcessor.applyTo(env, null, null, List.of(profiles));
        return env;
    }

    private static List<String> flywayLocations(StandardEnvironment env) {
        return Binder.get(env).bind("spring.flyway.locations", Bindable.listOf(String.class)).orElse(List.of());
    }
}
