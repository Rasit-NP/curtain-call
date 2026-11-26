package com.curtaincall.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트의 기반. 상속해서 쓴다.
 *
 * <ul>
 *   <li>모든 통합 테스트가 PostgreSQL 17 컨테이너 하나를 공유한다. JVM에서 한 번 띄우고 @ServiceConnection으로 연결한다.
 *       테스트 클래스마다 멈추지 않도록 @Testcontainers/@Container를 쓰지 않는다.</li>
 *   <li>테스트마다 시작 전에 시계를 {@link MutableClock#DEFAULT_INSTANT}로 되돌리고, Flyway 이력 테이블을 뺀 모든 테이블을 비운다.</li>
 *   <li>@Transactional 롤백을 쓰지 않는다.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    @BeforeEach
    void resetSharedState() {
        clock.reset();
        databaseCleaner.truncateAllTables();
    }
}
