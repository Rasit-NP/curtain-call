package com.curtaincall.e2e;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E 백엔드의 DB. 실행마다 새로 만드는 PostgreSQL 17 컨테이너이고(운영·통합 테스트와 같은 종류), 빈 포트에 열린다.
 * {@code @ServiceConnection}이 이 컨테이너의 주소와 계정으로 데이터 소스를 만든다(e2e 프로필에는 DB 주소가 없다, 결정 0161).
 */
@Configuration(proxyBeanMethods = false)
class E2eDatabase {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
