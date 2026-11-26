package com.curtaincall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @ParameterizedTest
    @ValueSource(strings = {"local", "e2e"})
    @DisplayName("local과 e2e는 환경변수 없이 compose DB에 붙고 마이그레이션과 시드를 쓴다")
    void localAndE2eUseComposeDbAndSeed(String profile) {
        StandardEnvironment env = load(Map.of(), profile);

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(COMPOSE_DB_URL);
        assertThat(env.getProperty("spring.datasource.username")).isNotBlank();
        assertThat(env.getProperty("spring.datasource.password")).isNotBlank();
        assertThat(flywayLocations(env)).isEqualTo(MIGRATION_AND_SEED);
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
