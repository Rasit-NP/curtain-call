package com.curtaincall.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.curtaincall.auth.domain.UserRepository;

@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {

    /** 운영 Clock 빈 대신 주입된다. */
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate, Flyway flyway) {
        return new DatabaseCleaner(jdbcTemplate, flyway);
    }

    @Bean
    TestUsers testUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return new TestUsers(userRepository, passwordEncoder);
    }
}
