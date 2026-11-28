package com.curtaincall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.curtaincall.support.FreshDatabases;
import com.curtaincall.testsupport.api.TestDataResetController;
import com.curtaincall.testsupport.application.TestDataResetService;
import com.jayway.jsonpath.JsonPath;

/**
 * 운영(prod) 프로필로 애플리케이션을 띄워 배포 서버와 같은 설정을 확인한다(Issue #71, spec 6.9, 6.10, R6).
 *
 * <ul>
 *   <li>DB 접속 정보와 JWT 서명 키는 운영처럼 환경변수 이름(DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET)으로만 넘긴다.
 *       {@code @ServiceConnection}을 쓰지 않아 application.yml의 prod 문서가 실제로 쓰인다.</li>
 *   <li>DB는 빈 DB에서 시작해 prod 프로필의 Flyway 위치로 마이그레이션한다. test 프로필 컨텍스트와 DB를 나누고({@link FreshDatabases}),
 *       테이블을 비우지 않는다. 테스트는 이 클래스 안에서 데이터를 바꾸지 않는다.</li>
 *   <li>로그인과 토큰 사용은 실제 흐름(로그인 API로 받은 토큰)으로 한다.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileTest {

    private static final FreshDatabases.Database DATABASE = FreshDatabases.create("prod_profile");
    /** 테스트 전용 값. 개발 키(이름에 dev)와 다른 32바이트 이상의 값이다. */
    private static final String JWT_SECRET = "prod-profile-test-signing-key-0123456789abcdef";
    private static final String DEMO_DATA = "classpath:db/demo/*.sql";
    private static final String RESET = "/api/test-support/reset";
    private static final List<String> DEMO_USERNAMES = List.of("demo1", "demo2", "demo3", "demo4", "demo5");

    @DynamicPropertySource
    static void productionEnvironment(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", DATABASE::jdbcUrl);
        registry.add("DB_USERNAME", DATABASE::username);
        registry.add("DB_PASSWORD", DATABASE::password);
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("prod 프로필로 환경변수의 DB에 붙어 뜨고, GET /actuator/health는 200 {\"status\":\"UP\"}이다")
    void startsWithProdProfileAndHealthIsUp() throws Exception {
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo(DATABASE.jdbcUrl().substring(DATABASE.jdbcUrl().lastIndexOf('/') + 1));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"demo1", "demo2", "demo3", "demo4", "demo5"})
    @DisplayName("운영 시연 계정 demo1~demo5는 비밀번호 demo1234로 로그인된다")
    void demoAccountsCanLogIn(String username) throws Exception {
        login(username, "demo1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "e2e-user1, e2e-pass1",
            "e2e-user2, e2e-pass2",
            "demo, demo1234",
    })
    @DisplayName("운영에는 E2E 계정과 로컬 시드의 시연 계정(demo)이 없어 로그인되지 않는다(401 INVALID_CREDENTIALS)")
    void nonProdSeedAccountsCannotLogIn(String username, String password) throws Exception {
        expectProblem(login(username, password), 401, "INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("운영 DB의 계정은 시연 계정 다섯 개뿐이다")
    void onlyDemoAccountsExist() {
        assertThat(jdbcTemplate.queryForList("SELECT username FROM users ORDER BY username", String.class))
                .isEqualTo(DEMO_USERNAMES);
    }

    @Test
    @DisplayName("운영 DB에는 배치가 서로 다른 공연장이 2곳 이상 있고 통로나 빈자리가 있는 배치가 있다. 공연과 공연 좌석은 넣지 않는다")
    void venuesWithoutPerformances() {
        Integer distinctLayouts = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT layout) FROM (
                    SELECT string_agg(seat_row || '-' || seat_number || '@' || grid_row || ',' || grid_column, ' '
                                      ORDER BY grid_row, grid_column) AS layout
                    FROM venue_seats GROUP BY venue_id
                ) AS layouts""", Integer.class);
        assertThat(distinctLayouts).isGreaterThanOrEqualTo(2);

        Integer venuesWithGaps = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT venue_id FROM venue_seats GROUP BY venue_id
                    HAVING count(*) < max(grid_row) * max(grid_column)
                ) AS gapped""", Integer.class);
        assertThat(venuesWithGaps).isPositive();

        Integer venuesWithoutSeats = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM venues v WHERE NOT EXISTS (SELECT 1 FROM venue_seats s WHERE s.venue_id = v.id)""",
                Integer.class);
        assertThat(venuesWithoutSeats).isZero();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM performances", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM seats", Integer.class)).isZero();
    }

    @Test
    @DisplayName("시연 데이터 스크립트를 이미 들어간 DB에 다시 실행해도 계정·공연장·배치가 늘거나 바뀌지 않는다(운영 데이터를 덮어쓰지 않는다)")
    void demoDataScriptsAreIdempotent() throws IOException {
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources(DEMO_DATA);
        assertThat(scripts).as(DEMO_DATA).isNotEmpty();
        Arrays.sort(scripts, Comparator.comparing(Resource::getFilename));
        Map<String, List<Map<String, Object>>> before = snapshotDemoData();

        new ResourceDatabasePopulator(scripts).execute(dataSource);

        assertThat(snapshotDemoData()).isEqualTo(before);
    }

    @Test
    @DisplayName("R6: prod 프로필에서 POST /api/test-support/reset은 토큰이 없으면 401 AUTH_REQUIRED다")
    void resetWithoutTokenIsAuthRequired() throws Exception {
        expectProblem(mockMvc.perform(post(RESET)), 401, "AUTH_REQUIRED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    @DisplayName("R6: prod 프로필에서 POST /api/test-support/reset은 시연 계정으로 로그인한 유효한 토큰이 있어도 404 NOT_FOUND이고 데이터를 지우지 않는다")
    void resetWithValidTokenIsNotFound() throws Exception {
        String token = JsonPath.read(login("demo1", "demo1234")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        Map<String, List<Map<String, Object>>> before = snapshotDemoData();

        expectProblem(mockMvc.perform(post(RESET).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
                404, "NOT_FOUND");

        assertThat(snapshotDemoData()).isEqualTo(before);
    }

    @Test
    @DisplayName("prod 프로필의 실제 컨텍스트에는 초기화 컨트롤러와 서비스가 등록되지 않는다")
    void resetBeansAreNotRegistered() {
        assertThat(context.getBeanNamesForType(TestDataResetController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TestDataResetService.class)).isEmpty();
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    /** 시연 데이터가 넣는 테이블의 모든 행(id 포함). */
    private Map<String, List<Map<String, Object>>> snapshotDemoData() {
        return Map.of(
                "users", jdbcTemplate.queryForList("SELECT * FROM users ORDER BY id"),
                "venues", jdbcTemplate.queryForList("SELECT * FROM venues ORDER BY id"),
                "venue_seats", jdbcTemplate.queryForList("SELECT * FROM venue_seats ORDER BY id"));
    }
}
