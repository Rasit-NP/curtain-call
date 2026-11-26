package com.curtaincall.hold.api;

import static com.curtaincall.support.HoldRequests.body;
import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.curtaincall.support.AuthRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPerformances;
import com.curtaincall.support.TestTokens;
import com.jayway.jsonpath.JsonPath;

/**
 * 좌석 묶음 선점 API(spec 6.4)와 R1·R2·R3·R6. 동시에 오는 요청은 {@code HoldConcurrencyTest}가 다룬다.
 *
 * <p>테스트 데이터는 테스트마다 직접 만든다. 인증은 {@link AuthRequests#asUser(long)}({@code jwt()})로 하고, 실제로 서명한 토큰과
 * 로그인 흐름은 R6 테스트에서 따로 본다. 실패한 요청은 응답과 함께 DB 최종 상태(선점 행, 좌석의 저장 상태와 holdId)로 "어떤 좌석도
 * 선점되지 않았음"을 확인한다.
 */
class HoldApiTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");
    private static final int PRICE = 30000;
    /** R3: 선점은 5분간 유효하다. 구현의 상수를 쓰지 않고 명세 값을 그대로 쓴다. */
    private static final Duration VALIDITY = Duration.ofMinutes(5);

    @Autowired
    private TestPerformances performances;

    @Autowired
    private TestHolds holds;

    @Autowired
    private JwtEncoder jwtEncoder;

    private long alice;
    private long bob;
    private long venue;
    private long performance;
    private long a1;
    private long a2;
    private long a3;
    private long a4;
    private long a5;
    private long a6;
    private long a7;
    private long a8;

    @BeforeEach
    void createUsersAndSeats() {
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
        venue = performances.createVenue("선점홀");
        performance = performances.createPerformance("선점 공연", venue, STARTS_AT, PRICE);
        a1 = performances.addSeat(performance, "A", 1, 1, 1);
        a2 = performances.addSeat(performance, "A", 2, 1, 2);
        a3 = performances.addSeat(performance, "A", 3, 1, 3);
        a4 = performances.addSeat(performance, "A", 4, 1, 4);
        a5 = performances.addSeat(performance, "A", 5, 1, 5);
        a6 = performances.addSeat(performance, "A", 6, 1, 6);
        a7 = performances.addSeat(performance, "A", 7, 1, 7);
        a8 = performances.addSeat(performance, "A", 8, 1, 8);
    }

    @Test
    @DisplayName("R1: 선택 가능한 좌석 묶음을 선점하면 201을 받고 묶음의 모든 좌석이 HELD로 보인다")
    void holdingAvailableSeatsShowsThemHeld() throws Exception {
        long hold = createHold(alice, performance, a1, a2);

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[?(@.status == 'HELD')].id", containsInAnyOrder(asInt(a1), asInt(a2))))
                .andExpect(jsonPath("$.seats[?(@.status == 'AVAILABLE')]", hasSize(6)));
        assertThat(holds.seatHoldId(a1)).isEqualTo(hold);
        assertThat(holds.seatHoldId(a2)).isEqualTo(hold);
        assertThat(holds.seatIdsOf(hold)).containsExactly(a1, a2);
        assertThat(holds.userIdOf(hold)).isEqualTo(alice);
    }

    @Test
    @DisplayName("선점에 성공하면 201 Hold다: status ACTIVE, performance는 PerformanceSummary, seats는 row·number 오름차순의 SeatRef, "
            + "amount는 가격 × 좌석 수, expiresAt은 요청 시각 + 5분")
    void createdHoldShape() throws Exception {
        // 요청 순서, id 순서와 다른 표시 순서(A-2, A-10, B-1)를 만든다. number는 숫자 순(A-2가 A-10보다 앞)이다.
        long b1 = performances.addSeat(performance, "B", 1, 2, 1);
        long a10 = performances.addSeat(performance, "A", 10, 1, 10);
        clock.setInstant(Instant.parse("2026-12-01T10:00:30Z"));

        MvcResult result = mockMvc.perform(hold(alice, performance, b1, a10, a2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.performance.*", hasSize(5)))
                .andExpect(jsonPath("$.performance.id").value(performance))
                .andExpect(jsonPath("$.performance.title").value("선점 공연"))
                .andExpect(jsonPath("$.performance.venue").value("선점홀"))
                .andExpect(jsonPath("$.performance.startsAt").value("2026-12-20T10:00:00Z"))
                .andExpect(jsonPath("$.performance.price").value(PRICE))
                .andExpect(jsonPath("$.seats", hasSize(3)))
                .andExpect(jsonPath("$.seats[0].*", hasSize(3)))
                .andExpect(jsonPath("$.seats[*].id", contains(asInt(a2), asInt(a10), asInt(b1))))
                .andExpect(jsonPath("$.seats[*].row", contains("A", "A", "B")))
                .andExpect(jsonPath("$.seats[*].number", contains(2, 10, 1)))
                .andExpect(jsonPath("$.amount").value(PRICE * 3))
                .andExpect(jsonPath("$.expiresAt").value("2026-12-01T10:05:30Z"))
                .andReturn();

        assertThat(holds.seatIdsOf(holdId(result))).containsExactlyInAnyOrder(a2, a10, b1);
    }

    @Test
    @DisplayName("R1: 묶음 중 한 좌석이라도 이미 선점됐으면 409 SEAT_ALREADY_HELD이고 나머지 좌석도 선점되지 않는다")
    void bundleWithOneHeldSeatIsRejectedWhole() throws Exception {
        long bobsHold = createHold(bob, performance, a2);

        expectProblem(mockMvc.perform(hold(alice, performance, a1, a2, a3)), 409, "SEAT_ALREADY_HELD");

        assertAvailable(a1);
        assertAvailable(a3);
        assertThat(holds.seatHoldId(a2)).isEqualTo(bobsHold);
        assertThat(holds.countHolds()).isEqualTo(1);
    }

    @Test
    @DisplayName("R1: 판매된 좌석이나 내가 이미 선점한 좌석을 선점하면 409 SEAT_ALREADY_HELD다(보유 좌석 수는 한도 안에서)")
    void soldSeatOrMyOwnHeldSeatIsRejected() throws Exception {
        // 결제 단계 전이라 판매된 좌석은 저장 상태로 만든다(SOLD는 holdId가 없다).
        long sold = performances.addSeat(performance, "S", 1, 9, 1, "SOLD");
        long mine = createHold(alice, performance, a1);

        // 보유 1석 + 요청 1~2석은 4석 한도 안이라 한도(409 HOLD_LIMIT_EXCEEDED)가 아닌 좌석 검사에서 걸린다.
        expectProblem(mockMvc.perform(hold(alice, performance, sold)), 409, "SEAT_ALREADY_HELD");
        expectProblem(mockMvc.perform(hold(alice, performance, a1)), 409, "SEAT_ALREADY_HELD");
        expectProblem(mockMvc.perform(hold(alice, performance, a2, a1)), 409, "SEAT_ALREADY_HELD");

        assertThat(holds.seatStatus(sold)).isEqualTo("SOLD");
        assertThat(holds.seatHoldId(sold)).isNull();
        assertThat(holds.seatHoldId(a1)).isEqualTo(mine);
        assertAvailable(a2);
        assertThat(holds.countHolds()).isEqualTo(1);
    }

    @Test
    @DisplayName("R2: 보유 좌석이 없으면 4석 묶음을 선점할 수 있다")
    void fourSeatBundleWithoutHoldings() throws Exception {
        long hold = createHold(alice, performance, a1, a2, a3, a4);

        assertThat(holds.seatIdsOf(hold)).containsExactly(a1, a2, a3, a4);
        assertThat(holds.heldSeatIds(performance)).containsExactly(a1, a2, a3, a4);
    }

    @Test
    @DisplayName("R2: 4석 선점이 만료된 순간 새로 4석을 선점할 수 있다")
    void newFourSeatsAtTheMomentFourSeatHoldExpires() throws Exception {
        createHold(alice, performance, a1, a2, a3, a4);
        Instant expiresAt = clock.instant().plus(VALIDITY);

        clock.setInstant(expiresAt.minusSeconds(1));
        expectProblem(mockMvc.perform(hold(alice, performance, a5)), 409, "HOLD_LIMIT_EXCEEDED");
        assertAvailable(a5);

        clock.setInstant(expiresAt);
        long renewed = createHold(alice, performance, a5, a6, a7, a8);
        assertThat(holds.seatIdsOf(renewed)).containsExactly(a5, a6, a7, a8);
    }

    @Test
    @DisplayName("R2: 5석 묶음은 409 HOLD_LIMIT_EXCEEDED이고 아무 좌석도 선점되지 않는다")
    void fiveSeatBundleIsRejected() throws Exception {
        expectProblem(mockMvc.perform(hold(alice, performance, a1, a2, a3, a4, a5)), 409, "HOLD_LIMIT_EXCEEDED");

        assertThat(holds.heldSeatIds(performance)).isEmpty();
        assertThat(holds.countHolds()).isZero();
    }

    @Test
    @DisplayName("R2: 다른 공연의 선점은 보유 좌석 수에 넣지 않는다(다른 공연에 4석이 있어도 4석을 선점할 수 있다)")
    void holdsOfOtherPerformanceDoNotCount() throws Exception {
        long other = performances.createPerformance("다른 회차", venue, STARTS_AT.plus(Duration.ofDays(1)), PRICE);
        long o1 = performances.addSeat(other, "A", 1, 1, 1);
        long o2 = performances.addSeat(other, "A", 2, 1, 2);
        long o3 = performances.addSeat(other, "A", 3, 1, 3);
        long o4 = performances.addSeat(other, "A", 4, 1, 4);
        createHold(alice, other, o1, o2, o3, o4);

        long hold = createHold(alice, performance, a1, a2, a3, a4);

        assertThat(holds.seatIdsOf(hold)).containsExactly(a1, a2, a3, a4);
    }

    @Test
    @DisplayName("R3: 만료 시각 1초 전에는 다른 사용자가 선점할 수 없다(409 SEAT_ALREADY_HELD)")
    void otherUserCannotHoldOneSecondBeforeExpiry() throws Exception {
        long alicesHold = createHold(alice, performance, a1);
        clock.advance(VALIDITY.minusSeconds(1));

        expectProblem(mockMvc.perform(hold(bob, performance, a1)), 409, "SEAT_ALREADY_HELD");

        assertThat(holds.seatHoldId(a1)).isEqualTo(alicesHold);
        assertThat(holds.countHolds()).isEqualTo(1);
    }

    @Test
    @DisplayName("R3: 만료 시각과 같은 순간에는 다른 사용자가 선점할 수 있다")
    void otherUserCanHoldAtTheMomentOfExpiry() throws Exception {
        long alicesHold = createHold(alice, performance, a1, a2);
        clock.advance(VALIDITY);

        long bobsHold = createHold(bob, performance, a1);

        // 좌석을 잡은 선점은 좌석의 holdId다(결정 0009). 새 선점이 덮어쓴 좌석도 만료된 선점의 좌석 목록에는 남는다.
        assertThat(holds.seatHoldId(a1)).isEqualTo(bobsHold);
        assertThat(holds.seatIdsOf(bobsHold)).containsExactly(a1);
        assertThat(holds.seatIdsOf(alicesHold)).containsExactly(a1, a2);
        // 덮어쓰지 않은 좌석은 저장 상태가 그대로다(지연 만료). API 상태는 공연 상세 테스트(R3)가 본다.
        assertThat(holds.seatHoldId(a2)).isEqualTo(alicesHold);
    }

    @Test
    @DisplayName("R6: 유효한 토큰이면 선점을 할 수 있다(같은 요청에 토큰이 없으면 401 AUTH_REQUIRED)")
    void validTokenCanHold() throws Exception {
        String token = TestTokens.sign(jwtEncoder, alice, clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        expectProblem(mockMvc.perform(post("/api/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(performance, a1))), 401, "AUTH_REQUIRED");
        assertThat(holds.countHolds()).isZero();

        MvcResult created = mockMvc.perform(post("/api/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(performance, a1)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(holds.userIdOf(holdId(created))).isEqualTo(alice);
    }

    @Test
    @DisplayName("R6: 로그인 API로 받은 토큰으로 선점까지 할 수 있다(실제 흐름)")
    void loginTokenCanHold() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"alice-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        MvcResult created = mockMvc.perform(post("/api/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(performance, a1, a2)))
                .andExpect(status().isCreated())
                .andReturn();

        long hold = holdId(created);
        assertThat(holds.userIdOf(hold)).isEqualTo(alice);
        assertThat(holds.seatIdsOf(hold)).containsExactly(a1, a2);
    }

    @Test
    @DisplayName("없는 공연이나 그 공연에 없는 좌석으로 선점하면 404 NOT_FOUND이고 아무 좌석도 선점되지 않는다(그 좌석을 빼면 선점된다)")
    void unknownPerformanceOrSeatIsNotFound() throws Exception {
        long other = performances.createPerformance("다른 회차", venue, STARTS_AT.plus(Duration.ofDays(1)), PRICE);
        long foreign = performances.addSeat(other, "A", 1, 1, 1);
        long missingPerformance = Math.max(performance, other) + 1000;
        long missingSeat = Math.max(a8, foreign) + 1000;

        expectProblem(mockMvc.perform(hold(alice, missingPerformance, a1)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(hold(alice, performance, a1, foreign)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(hold(alice, performance, a1, missingSeat)), 404, "NOT_FOUND");
        assertThat(holds.countHolds()).isZero();
        assertAvailable(a1);
        assertAvailable(foreign);

        createHold(alice, performance, a1);
    }

    @Test
    @DisplayName("seatIds가 아주 많아도(7만 개) 500이 아니라 표의 오류로 끝난다(없는 좌석이 있으면 404 NOT_FOUND)")
    void veryLongSeatIdListEndsWithTableError() throws Exception {
        String manyMissing = LongStream.rangeClosed(1, 70_000).map(i -> a8 + 1000 + i)
                .mapToObj(Long::toString).collect(Collectors.joining(","));

        expectProblem(mockMvc.perform(post("/api/holds")
                .with(AuthRequests.asUser(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"performanceId\":" + performance + ",\"seatIds\":[" + a1 + "," + manyMissing + "]}")),
                404, "NOT_FOUND");
        assertThat(holds.countHolds()).isZero();

        createHold(alice, performance, a1);
    }

    static Stream<Arguments> invalidBodies() {
        // {p}는 있는 공연, {s}는 그 공연의 선택 가능한 좌석으로 바꾼다. 400이 아니면 그대로 선점될 값이다.
        return Stream.of(
                Arguments.of("빈 객체", "{}"),
                Arguments.of("performanceId 없음", "{\"seatIds\":[{s}]}"),
                Arguments.of("performanceId가 null", "{\"performanceId\":null,\"seatIds\":[{s}]}"),
                Arguments.of("performanceId가 0", "{\"performanceId\":0,\"seatIds\":[{s}]}"),
                Arguments.of("performanceId가 음수", "{\"performanceId\":-1,\"seatIds\":[{s}]}"),
                Arguments.of("seatIds 없음", "{\"performanceId\":{p}}"),
                Arguments.of("seatIds가 null", "{\"performanceId\":{p},\"seatIds\":null}"),
                Arguments.of("seatIds가 빈 배열", "{\"performanceId\":{p},\"seatIds\":[]}"),
                Arguments.of("seatIds에 중복", "{\"performanceId\":{p},\"seatIds\":[{s},{s}]}"),
                Arguments.of("seatIds에 0", "{\"performanceId\":{p},\"seatIds\":[{s},0]}"),
                Arguments.of("seatIds에 음수", "{\"performanceId\":{p},\"seatIds\":[{s},-1]}"),
                Arguments.of("seatIds에 null", "{\"performanceId\":{p},\"seatIds\":[{s},null]}"),
                Arguments.of("seatIds가 배열이 아님", "{\"performanceId\":{p},\"seatIds\":{s}}"),
                Arguments.of("performanceId가 소수(잘라 받지 않는다)", "{\"performanceId\":{p}.5,\"seatIds\":[{s}]}"),
                Arguments.of("performanceId가 지수 표기", "{\"performanceId\":{p}e0,\"seatIds\":[{s}]}"),
                Arguments.of("performanceId가 문자열", "{\"performanceId\":\"{p}\",\"seatIds\":[{s}]}"),
                Arguments.of("seatIds에 소수점이 있는 수", "{\"performanceId\":{p},\"seatIds\":[{s}.0]}"),
                Arguments.of("seatIds에 문자열", "{\"performanceId\":{p},\"seatIds\":[\"{s}\"]}"),
                Arguments.of("performanceId가 long 범위 밖", "{\"performanceId\":99999999999999999999,\"seatIds\":[{s}]}"),
                Arguments.of("JSON이 아님", "{\"performanceId\":{p},\"seatIds\":[{s}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("performanceId·seatIds가 없거나, 비었거나, 양수가 아니거나, 정수가 아니거나(소수·문자열), seatIds에 중복이 있으면 "
            + "400 INVALID_INPUT이고 아무 좌석도 선점되지 않는다")
    void invalidBodyIsInvalidInput(String description, String template) throws Exception {
        String content = template.replace("{p}", String.valueOf(performance)).replace("{s}", String.valueOf(a1));

        expectProblem(mockMvc.perform(post("/api/holds")
                .with(AuthRequests.asUser(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)), 400, "INVALID_INPUT");

        assertThat(holds.countHolds()).isZero();
        assertAvailable(a1);
    }

    @Test
    @DisplayName("선점 오류는 6.4 표의 순서로 처음 걸린 하나만 돌려준다(401 → 400 → 404 → 409 HOLD_LIMIT_EXCEEDED → 409 SEAT_ALREADY_HELD)")
    void errorsFollowTheTableOrder() throws Exception {
        long other = performances.createPerformance("다른 회차", venue, STARTS_AT.plus(Duration.ofDays(1)), PRICE);
        long foreign = performances.addSeat(other, "A", 1, 1, 1);
        long missingPerformance = Math.max(performance, other) + 1000;
        long bobsHold = createHold(bob, performance, a5);

        // 401이 400보다 먼저: 토큰 없이 잘못된 본문
        expectProblem(mockMvc.perform(post("/api/holds").contentType(MediaType.APPLICATION_JSON).content("{}")),
                401, "AUTH_REQUIRED");
        // 400이 404보다 먼저: 없는 공연에 빈 좌석 목록
        expectProblem(mockMvc.perform(post("/api/holds")
                .with(AuthRequests.asUser(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"performanceId\":" + missingPerformance + ",\"seatIds\":[]}")), 400, "INVALID_INPUT");
        // 404가 409 HOLD_LIMIT_EXCEEDED보다 먼저: 5석 중 하나가 다른 공연의 좌석
        expectProblem(mockMvc.perform(hold(alice, performance, a1, a2, a3, a4, foreign)), 404, "NOT_FOUND");
        // 409 HOLD_LIMIT_EXCEEDED가 409 SEAT_ALREADY_HELD보다 먼저: 5석 중 하나가 다른 사용자가 선점한 좌석
        expectProblem(mockMvc.perform(hold(alice, performance, a1, a2, a3, a4, a5)), 409, "HOLD_LIMIT_EXCEEDED");

        assertThat(holds.countHolds()).isEqualTo(1);
        assertThat(holds.heldSeatIds(performance)).containsExactly(a5);
        assertThat(holds.seatHoldId(a5)).isEqualTo(bobsHold);
    }

    /**
     * 토큰은 서명·만료만 검증하므로(DB를 보지 않는다) 토큰의 사용자가 DB에 없을 수 있다. 사용자 삭제 기능이 없어 정상 흐름에서는 생기지 않으며,
     * 따로 처리하지 않기로 했다(결정 0071): 선점을 저장할 때 외래 키가 막아 500이 되고, 트랜잭션이 되돌려져 아무 좌석도 선점되지 않는다.
     */
    @Test
    @DisplayName("토큰의 사용자가 DB에 없으면 따로 처리하지 않아 500 INTERNAL_ERROR이고 아무 좌석도 선점되지 않는다")
    void userMissingFromDatabaseIsNotHandledSeparately() throws Exception {
        long missingUser = Math.max(alice, bob) + 1000;

        expectProblem(mockMvc.perform(hold(missingUser, performance, a1, a2)), 500, "INTERNAL_ERROR");

        assertThat(holds.countHolds()).isZero();
        assertAvailable(a1);
        assertAvailable(a2);
    }

    private long createHold(long userId, long performanceId, long... seatIds) throws Exception {
        return holdId(mockMvc.perform(hold(userId, performanceId, seatIds))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void assertAvailable(long seat) {
        assertThat(holds.seatStatus(seat)).isEqualTo("AVAILABLE");
        assertThat(holds.seatHoldId(seat)).isNull();
    }

    private static void expectProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** JsonPath는 작은 정수를 Integer로 읽는다. 테스트 데이터의 id는 작으므로 int로 바꿔 비교한다. */
    private static int asInt(long id) {
        return Math.toIntExact(id);
    }
}
