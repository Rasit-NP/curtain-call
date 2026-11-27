package com.curtaincall.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import com.curtaincall.support.HoldRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.PaymentRequests;
import com.curtaincall.support.SeedFiles;
import com.curtaincall.support.TestPerformances;
import com.jayway.jsonpath.JsonPath;

/**
 * E2E 데이터 초기화 {@code POST /api/test-support/reset}(spec 6.9).
 *
 * <p>test 프로필에 e2e를 더해 띄운다(DB는 Testcontainers). 테스트마다 테이블을 비운 뒤 시드 파일을 직접 실행해 local·e2e의 처음 상태를
 * 만들고(결정 0036과 같은 방식), 그때의 시드 내용(계정, 공연장·배치, 공연, 좌석과 저장 상태)을 기억한다. 선점·결제로 데이터를 만들고
 * 시드를 바꾼 뒤 초기화해, 시드가 넣지 않는 테이블은 비고 시드 내용은 처음과 같아지는지 본다.
 *
 * <p>시드 내용은 id가 아니라 이름(아이디, 공연장 이름, 공연 제목·일시, 좌석 표시 이름)으로 비교한다. E2E는 초기화 뒤의 id를 가정하지 않는다(spec 6.9).
 * 시드 값(제목, 좌석 수)은 보지 않고 처음 상태와 같은지만 본다(spec 2.5).
 */
@ActiveProfiles("e2e")
class TestDataResetApiTest extends IntegrationTest {

    private static final String RESET = "/api/test-support/reset";

    /** 시드가 넣는 테이블. 이 밖의 테이블(Flyway 이력 제외)은 초기화 뒤 비어 있어야 한다. */
    private static final List<String> SEED_TABLES = List.of("users", "venues", "venue_seats", "performances", "seats");

    /** 선점·결제가 남기는 테이블. 초기화 전에 데이터가 있는지 확인해, '비어 있다'는 확인이 헛돌지 않게 한다. */
    private static final List<String> ACTIVITY_TABLES =
            List.of("holds", "hold_seats", "reservations", "reservation_seats", "payments", "idempotency_records");

    private static final int MIN_PERFORMANCES = 2;
    private static final int MIN_AVAILABLE_SEATS = 5;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestPerformances testPerformances;

    private Map<String, List<Map<String, Object>>> initialSeed;

    @BeforeEach
    void applySeed() {
        SeedFiles.apply(dataSource);
        initialSeed = seedContents();
    }

    @Test
    @DisplayName("e2e 프로필에서 POST /api/test-support/reset은 토큰 없이 204이고 본문이 없다")
    void resetIsNoContentWithoutToken() throws Exception {
        mockMvc.perform(post(RESET))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("초기화는 선점·예매·결제·멱등 기록을 비우고, 좌석 상태와 계정·공연 시드(바꾼 값, 더한 행 포함)를 처음 상태로 되돌린다")
    void resetClearsActivityAndRestoresSeed() throws Exception {
        assertThat(tablesWithRows()).as("시드가 넣는 테이블").containsExactlyInAnyOrderElementsOf(SEED_TABLES);
        makeActivityAndChangeSeed();
        assertThat(otherThanSeedTables()).containsAll(ACTIVITY_TABLES);
        for (String table : ACTIVITY_TABLES) {
            assertThat(count(table)).as("초기화 전 %s", table).isPositive();
        }
        assertThat(seedContents()).isNotEqualTo(initialSeed);

        mockMvc.perform(post(RESET)).andExpect(status().isNoContent());

        for (String table : otherThanSeedTables()) {
            assertThat(count(table)).as("초기화 뒤 %s", table).isZero();
        }
        assertThat(seedContents()).isEqualTo(initialSeed);
    }

    @Test
    @DisplayName("초기화를 두 번 연달아 불러도 같은 상태다")
    void resetTwiceGivesSameState() throws Exception {
        makeActivityAndChangeSeed();

        mockMvc.perform(post(RESET)).andExpect(status().isNoContent());
        Map<String, List<Map<String, Object>>> afterFirst = seedContents();
        mockMvc.perform(post(RESET)).andExpect(status().isNoContent());

        assertThat(seedContents()).isEqualTo(afterFirst).isEqualTo(initialSeed);
        for (String table : otherThanSeedTables()) {
            assertThat(count(table)).as("두 번째 초기화 뒤 %s", table).isZero();
        }
    }

    @Test
    @DisplayName("시드가 없는 빈 DB에서 불러도 시드를 넣는다(지우고 다시 넣으며, 이전 상태에 기대지 않는다)")
    void resetFromEmptyDatabaseInsertsSeed() throws Exception {
        databaseCleaner.truncateAllTables();
        assertThat(tablesWithRows()).isEmpty();

        mockMvc.perform(post(RESET)).andExpect(status().isNoContent());

        assertThat(seedContents()).isEqualTo(initialSeed);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "demo, demo1234",
            "e2e-user1, e2e-pass1",
            "e2e-user2, e2e-pass2",
    })
    @DisplayName("초기화 뒤에도 시드 계정으로 로그인할 수 있다(초기화 전에 비밀번호를 바꿔 둔다)")
    void seedAccountsCanLogInAfterReset(String username, String password) throws Exception {
        makeActivityAndChangeSeed();
        login(username, password).andExpect(status().isUnauthorized());

        mockMvc.perform(post(RESET)).andExpect(status().isNoContent());

        login(username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    @DisplayName("초기화 뒤 시드 조건(2.5)을 만족한다: 공연 2개 이상, 공연 상세마다 빈 좌석 5석 이상(초기화 전에 빈 좌석을 모두 없앤다)")
    void seedMeetsSpecMinimumAfterReset() throws Exception {
        makeActivityAndChangeSeed();
        assertThat(availableSeatCounts()).as("초기화 전 공연별 빈 좌석 수").allMatch(available -> available == 0);

        mockMvc.perform(post(RESET)).andExpect(status().isNoContent());

        List<Integer> available = availableSeatCounts();
        assertThat(available).hasSizeGreaterThanOrEqualTo(MIN_PERFORMANCES);
        assertThat(available).as("공연별 빈 좌석 수").allMatch(count -> count >= MIN_AVAILABLE_SEATS);
    }

    /**
     * 초기화가 지워야 할 데이터와 되돌려야 할 시드 변경을 만든다.
     * <ul>
     *   <li>결제 승인: 선점 PAID, 좌석 SOLD, 예매·결제(SUCCEEDED)·멱등 기록(201)</li>
     *   <li>결제 거절: 선점 ACTIVE와 좌석 HELD 그대로, 결제(FAILED)·멱등 기록(402)</li>
     *   <li>시드 변경: 시드에 없는 계정·공연장·공연·좌석을 더하고, 모든 계정의 비밀번호와 공연 제목을 바꾸고, 남은 빈 좌석을 모두 SOLD로</li>
     * </ul>
     */
    private void makeActivityAndChangeSeed() throws Exception {
        long user = testUsers.create("reset-tester", "reset-pass");
        long performanceId = jdbcTemplate.queryForObject("SELECT min(id) FROM performances", Long.class);
        List<Long> seats = jdbcTemplate.queryForList(
                "SELECT id FROM seats WHERE performance_id = ? ORDER BY id LIMIT 3", Long.class, performanceId);

        long paid = HoldRequests.holdId(mockMvc.perform(HoldRequests.hold(user, performanceId, seats.get(0), seats.get(1)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(PaymentRequests.pay(user, paid)).andExpect(status().isCreated());

        long held = HoldRequests.holdId(mockMvc.perform(HoldRequests.hold(user, performanceId, seats.get(2)))
                .andExpect(status().isCreated()).andReturn());
        paymentGateway.declineNext();
        mockMvc.perform(PaymentRequests.pay(user, held)).andExpect(status().isPaymentRequired());

        long venueId = testPerformances.createVenue("초기화 확인용 공연장");
        long extra = testPerformances.createPerformance("초기화 확인용 공연", venueId, Instant.parse("2027-02-01T10:00:00Z"), 10000);
        testPerformances.addSeat(extra, "A", 1, 1, 1);
        jdbcTemplate.update("UPDATE users SET password_hash = ?", "{noop}changed-by-test");
        jdbcTemplate.update("UPDATE performances SET title = title || ' (바뀜)'");
        jdbcTemplate.update("UPDATE seats SET status = 'SOLD' WHERE status = 'AVAILABLE'");
    }

    /** 시드가 넣는 테이블의 내용. id 대신 이름으로 잇고, 정해진 순서로 읽는다. */
    private Map<String, List<Map<String, Object>>> seedContents() {
        Map<String, List<Map<String, Object>>> contents = new LinkedHashMap<>();
        contents.put("users", jdbcTemplate.queryForList("SELECT username, password_hash FROM users ORDER BY username"));
        contents.put("venues", jdbcTemplate.queryForList("SELECT name FROM venues ORDER BY name"));
        contents.put("venue_seats", jdbcTemplate.queryForList("""
                SELECT v.name AS venue, vs.seat_row, vs.seat_number, vs.grid_row, vs.grid_column
                FROM venue_seats vs JOIN venues v ON v.id = vs.venue_id
                ORDER BY v.name, vs.seat_row, vs.seat_number"""));
        contents.put("performances", jdbcTemplate.queryForList("""
                SELECT p.title, v.name AS venue, p.starts_at, p.price
                FROM performances p JOIN venues v ON v.id = p.venue_id
                ORDER BY p.starts_at, p.title, v.name"""));
        contents.put("seats", jdbcTemplate.queryForList("""
                SELECT p.title, p.starts_at, s.seat_row, s.seat_number, s.grid_row, s.grid_column, s.status, s.hold_id
                FROM seats s JOIN performances p ON p.id = s.performance_id
                ORDER BY p.starts_at, p.title, s.seat_row, s.seat_number"""));
        return contents;
    }

    /** Flyway 이력과 시드가 넣는 테이블을 뺀 테이블. */
    private List<String> otherThanSeedTables() {
        return databaseCleaner.tablesToTruncate().stream().filter(table -> !SEED_TABLES.contains(table)).toList();
    }

    /** 행이 하나라도 있는 테이블(Flyway 이력 제외). */
    private List<String> tablesWithRows() {
        return databaseCleaner.tablesToTruncate().stream().filter(table -> count(table) > 0).toList();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM \"" + table + "\"", Integer.class);
    }

    /** 공연 목록·상세 API로 본 공연별 빈 좌석(API 상태 AVAILABLE) 수. */
    private List<Integer> availableSeatCounts() throws Exception {
        String list = mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(list, "$[*].id");
        List<Integer> counts = new ArrayList<>();
        for (Number id : ids) {
            String detail = mockMvc.perform(get("/api/performances/{performanceId}", id.longValue()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<String> available = JsonPath.read(detail, "$.seats[?(@.status == 'AVAILABLE')].status");
            counts.add(available.size());
        }
        return counts;
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }
}
