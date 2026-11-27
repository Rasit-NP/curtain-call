package com.curtaincall.hold.api;

import static com.curtaincall.support.HoldRequests.getHold;
import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static com.curtaincall.support.HoldRequests.release;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.curtaincall.support.AuthRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.PaymentRequests;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPerformances;
import com.curtaincall.support.TestTokens;

/**
 * 내 선점 조회(spec 6.5)·해제(spec 6.6) API와 R2·R3·R6. 동시에 오는 해제는 {@code HoldConcurrencyTest}가 다룬다.
 *
 * <p>선점은 {@code POST /api/holds}로 만든다. 인증은 {@link AuthRequests#asUser(long)}({@code jwt()})로 하고, 실제로 서명한 토큰은
 * R6 테스트에서 따로 본다. 해제와 거부된 요청은 응답과 함께 DB 최종 상태(선점의 저장 상태, 좌석의 저장 상태와 holdId)를 확인한다.
 */
class HoldQueryAndReleaseApiTest extends IntegrationTest {

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

    @BeforeEach
    void createUsersAndSeats() {
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
        venue = performances.createVenue("해제홀");
        performance = performances.createPerformance("해제 공연", venue, STARTS_AT, PRICE);
        a1 = performances.addSeat(performance, "A", 1, 1, 1);
        a2 = performances.addSeat(performance, "A", 2, 1, 2);
        a3 = performances.addSeat(performance, "A", 3, 1, 3);
        a4 = performances.addSeat(performance, "A", 4, 1, 4);
        a5 = performances.addSeat(performance, "A", 5, 1, 5);
        a6 = performances.addSeat(performance, "A", 6, 1, 6);
    }

    // ---- 선점 조회(spec 6.5) ----

    @Test
    @DisplayName("내 선점을 조회하면 200 Hold다: 모양은 선점 생성 응답과 같고, seats는 row·number 오름차순의 SeatRef, "
            + "amount는 가격 × 좌석 수, expiresAt은 만든 시각 + 5분")
    void myHoldShape() throws Exception {
        // 요청 순서, id 순서와 다른 표시 순서(A2, A10, B1)를 만든다. number는 숫자 순(A2가 A10보다 앞)이다.
        long b1 = performances.addSeat(performance, "B", 1, 2, 1);
        long a10 = performances.addSeat(performance, "A", 10, 1, 10);
        clock.setInstant(Instant.parse("2026-12-01T10:00:30Z"));
        long hold = createHold(alice, performance, b1, a10, a2);
        // 조회 시각은 만든 시각과 다르다. expiresAt은 조회 시각이 아니라 만든 시각 + 5분이다.
        clock.advance(Duration.ofMinutes(1));

        mockMvc.perform(getHold(alice, hold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.id").value(hold))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.performance.*", hasSize(5)))
                .andExpect(jsonPath("$.performance.id").value(performance))
                .andExpect(jsonPath("$.performance.title").value("해제 공연"))
                .andExpect(jsonPath("$.performance.venue").value("해제홀"))
                .andExpect(jsonPath("$.performance.startsAt").value("2026-12-20T10:00:00Z"))
                .andExpect(jsonPath("$.performance.price").value(PRICE))
                .andExpect(jsonPath("$.seats", hasSize(3)))
                .andExpect(jsonPath("$.seats[0].*", hasSize(3)))
                .andExpect(jsonPath("$.seats[*].id", contains(asInt(a2), asInt(a10), asInt(b1))))
                .andExpect(jsonPath("$.seats[*].row", contains("A", "A", "B")))
                .andExpect(jsonPath("$.seats[*].number", contains(2, 10, 1)))
                .andExpect(jsonPath("$.amount").value(PRICE * 3))
                .andExpect(jsonPath("$.expiresAt").value("2026-12-01T10:05:30Z"));
    }

    @Test
    @DisplayName("R3: 선점 조회는 만료 시각 1초 전까지 status ACTIVE이고, 만료 시각부터 status EXPIRED를 돌려준다")
    void statusIsExpiredFromExpiresAt() throws Exception {
        long hold = createHold(alice, performance, a1, a2);
        Instant expiresAt = clock.instant().plus(VALIDITY);

        clock.setInstant(expiresAt.minusSeconds(1));
        mockMvc.perform(getHold(alice, hold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));

        clock.setInstant(expiresAt);
        mockMvc.perform(getHold(alice, hold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));

        clock.setInstant(expiresAt.plus(Duration.ofHours(1)));
        mockMvc.perform(getHold(alice, hold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        // 만료는 저장하지 않는다(지연 만료).
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("만료된 선점의 조회는 seats와 amount가 만든 때의 묶음이다(만료 뒤 다른 사용자가 새로 선점한 좌석 포함)")
    void expiredHoldShowsItsOriginalBundle() throws Exception {
        long alicesHold = createHold(alice, performance, a1, a2);
        clock.advance(VALIDITY);
        createHold(bob, performance, a1);

        mockMvc.perform(getHold(alice, alicesHold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.seats[*].id", contains(asInt(a1), asInt(a2))))
                .andExpect(jsonPath("$.amount").value(PRICE * 2));
    }

    @Test
    @DisplayName("다른 사용자의 선점, 해제된 선점, 없는 선점(0·음수 포함)의 조회는 404 NOT_FOUND다")
    void queryOfOthersReleasedOrMissingHoldIsNotFound() throws Exception {
        long bobsHold = createHold(bob, performance, a1);
        long released = createHold(alice, performance, a2);
        mockMvc.perform(release(alice, released)).andExpect(status().isNoContent());
        long missing = Math.max(bobsHold, released) + 1000;

        expectProblem(mockMvc.perform(getHold(alice, bobsHold)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(getHold(alice, released)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(getHold(alice, missing)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(getHold(alice, 0)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(getHold(alice, -1)), 404, "NOT_FOUND");

        // 주인에게는 보인다(404가 선점이 없어서가 아니라 다른 사용자의 선점이라서다).
        mockMvc.perform(getHold(bob, bobsHold)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonIntegerHoldIds")
    @DisplayName("선점 조회의 holdId가 정수가 아니면(문자, 소수, long 범위 밖, 공백·16진수·ASCII가 아닌 숫자) "
            + "내 선점을 가리키는 값이라도 400 INVALID_INPUT이다")
    void queryWithNonIntegerHoldIdIsInvalidInput(String notation, LongFunction<String> write) throws Exception {
        long hold = createHold(alice, performance, a1);

        expectProblem(mockMvc.perform(get("/api/holds/{holdId}", write.apply(hold)).with(AuthRequests.asUser(alice))),
                400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("선점 조회 오류는 6.5 표의 순서로 처음 걸린 하나만 돌려준다(401 → 400 → 404)")
    void queryErrorsFollowTheTableOrder() throws Exception {
        long bobsHold = createHold(bob, performance, a1);

        // 401이 400·404보다 먼저: 토큰 없이 정수가 아닌 holdId, 토큰 없이 다른 사용자의 선점
        expectProblem(mockMvc.perform(get("/api/holds/{holdId}", "abc")), 401, "AUTH_REQUIRED");
        expectProblem(mockMvc.perform(get("/api/holds/{holdId}", bobsHold)), 401, "AUTH_REQUIRED");
        // 400이 404보다 먼저: 다른 사용자의 선점(404)을 16진수로 쓴 값
        expectProblem(mockMvc.perform(get("/api/holds/{holdId}", hex(bobsHold)).with(AuthRequests.asUser(alice))),
                400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(getHold(alice, bobsHold)), 404, "NOT_FOUND");
    }

    // ---- 선점 해제(spec 6.6) ----

    @Test
    @DisplayName("내 선점을 해제하면 204이고 선점은 RELEASED, 그 좌석은 AVAILABLE이 된다(다른 선점의 좌석은 그대로)")
    void releasingMyHoldFreesItsSeats() throws Exception {
        long hold = createHold(alice, performance, a1, a2);
        long bobsHold = createHold(bob, performance, a3);

        mockMvc.perform(release(alice, hold))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(holds.holdStatus(hold)).isEqualTo("RELEASED");
        assertAvailable(a1);
        assertAvailable(a2);
        assertThat(holds.seatHoldId(a3)).isEqualTo(bobsHold);
        assertThat(holds.holdStatus(bobsHold)).isEqualTo("ACTIVE");
        // 선점의 좌석 목록(만든 때의 묶음)은 지우지 않는다.
        assertThat(holds.seatIdsOf(hold)).containsExactly(a1, a2);
        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].status", contains("AVAILABLE", "AVAILABLE", "HELD", "AVAILABLE",
                        "AVAILABLE", "AVAILABLE")));
    }

    @Test
    @DisplayName("해제한 뒤에는 그 선점의 조회와 해제가 모두 404 NOT_FOUND다")
    void releasedHoldIsNotFoundAfterwards() throws Exception {
        long hold = createHold(alice, performance, a1);
        mockMvc.perform(release(alice, hold)).andExpect(status().isNoContent());

        expectProblem(mockMvc.perform(getHold(alice, hold)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(release(alice, hold)), 404, "NOT_FOUND");

        assertThat(holds.holdStatus(hold)).isEqualTo("RELEASED");
        assertAvailable(a1);
    }

    @Test
    @DisplayName("결제된 선점의 조회는 404 NOT_FOUND다(만료 시각 전에도)")
    void queryOfPaidHoldIsNotFound() throws Exception {
        long hold = createHold(alice, performance, a1, a2);
        mockMvc.perform(PaymentRequests.pay(alice, hold)).andExpect(status().isCreated());

        expectProblem(mockMvc.perform(getHold(alice, hold)), 404, "NOT_FOUND");

        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("이미 결제된 선점의 해제는 404 NOT_FOUND이고 좌석은 SOLD 그대로다(만료 시각 전에도)")
    void releaseOfPaidHoldIsNotFound() throws Exception {
        long hold = createHold(alice, performance, a1, a2);
        mockMvc.perform(PaymentRequests.pay(alice, hold)).andExpect(status().isCreated());

        expectProblem(mockMvc.perform(release(alice, hold)), 404, "NOT_FOUND");

        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
        for (long seat : new long[] {a1, a2}) {
            assertThat(holds.seatStatus(seat)).isEqualTo("SOLD");
            assertThat(holds.seatHoldId(seat)).isNull();
        }
    }

    @Test
    @DisplayName("만료된 내 선점의 해제는 204이고 그 좌석은 AVAILABLE이 된다")
    void releasingMyExpiredHold() throws Exception {
        long hold = createHold(alice, performance, a1, a2);
        clock.advance(VALIDITY.plusMinutes(1));

        mockMvc.perform(release(alice, hold)).andExpect(status().isNoContent());

        assertThat(holds.holdStatus(hold)).isEqualTo("RELEASED");
        assertAvailable(a1);
        assertAvailable(a2);
    }

    @Test
    @DisplayName("R3: 만료된 내 선점을 해제해도 그 좌석을 새로 선점한 다른 사용자의 선점은 유지된다")
    void releasingExpiredHoldKeepsSeatsTakenByOthers() throws Exception {
        long alicesHold = createHold(alice, performance, a1, a2);
        clock.advance(VALIDITY);
        long bobsHold = createHold(bob, performance, a1);

        mockMvc.perform(release(alice, alicesHold)).andExpect(status().isNoContent());

        assertThat(holds.holdStatus(alicesHold)).isEqualTo("RELEASED");
        // 좌석을 잡은 선점은 좌석의 holdId다(결정 0009). holdId가 이 선점인 좌석(A2)만 되돌린다.
        assertThat(holds.seatStatus(a1)).isEqualTo("HELD");
        assertThat(holds.seatHoldId(a1)).isEqualTo(bobsHold);
        assertAvailable(a2);
        assertThat(holds.holdStatus(bobsHold)).isEqualTo("ACTIVE");
        mockMvc.perform(getHold(bob, bobsHold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.seats[*].id", contains(asInt(a1))));
        // 다른 사용자(앨리스)는 밥의 좌석을 선점할 수 없다.
        expectProblem(mockMvc.perform(hold(alice, performance, a1)), 409, "SEAT_ALREADY_HELD");
    }

    @Test
    @DisplayName("다른 사용자의 선점이나 없는 선점(0·음수 포함)을 해제하면 404 NOT_FOUND이고 선점과 좌석은 그대로다")
    void releasingOthersOrMissingHoldIsNotFound() throws Exception {
        long bobsHold = createHold(bob, performance, a1, a2);
        long missing = bobsHold + 1000;

        expectProblem(mockMvc.perform(release(alice, bobsHold)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(release(alice, missing)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(release(alice, 0)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(release(alice, -1)), 404, "NOT_FOUND");

        assertHeldBy(bobsHold, a1, a2);
    }

    @Test
    @DisplayName("만료된 다른 사용자의 선점을 해제하면 404 NOT_FOUND이고 선점과 좌석은 그대로다")
    void releasingOthersExpiredHoldIsNotFound() throws Exception {
        long bobsHold = createHold(bob, performance, a1);
        clock.advance(VALIDITY);

        expectProblem(mockMvc.perform(release(alice, bobsHold)), 404, "NOT_FOUND");

        assertHeldBy(bobsHold, a1);
    }

    @Test
    @DisplayName("R2: 선점을 해제하면 그 좌석은 보유 좌석 수에서 빠져 다시 4석을 선점할 수 있다")
    void releasedSeatsLeaveTheHeldSeatCount() throws Exception {
        long hold = createHold(alice, performance, a1, a2, a3, a4);
        expectProblem(mockMvc.perform(hold(alice, performance, a5)), 409, "HOLD_LIMIT_EXCEEDED");

        mockMvc.perform(release(alice, hold)).andExpect(status().isNoContent());

        // 해제한 좌석 2석과 새 좌석 2석을 한 묶음으로 선점한다(보유 0석 + 4석).
        long renewed = createHold(alice, performance, a1, a2, a5, a6);
        assertThat(holds.seatIdsOf(renewed)).containsExactly(a1, a2, a5, a6);
        assertThat(holds.heldSeatIds(performance)).containsExactly(a1, a2, a5, a6);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonIntegerHoldIds")
    @DisplayName("선점 해제의 holdId가 정수가 아니면(문자, 소수, long 범위 밖, 공백·16진수·ASCII가 아닌 숫자) "
            + "내 선점을 가리키는 값이라도 400 INVALID_INPUT이고 선점은 그대로다")
    void releaseWithNonIntegerHoldIdIsInvalidInput(String notation, LongFunction<String> write) throws Exception {
        long hold = createHold(alice, performance, a1);

        expectProblem(mockMvc.perform(delete("/api/holds/{holdId}", write.apply(hold)).with(AuthRequests.asUser(alice))),
                400, "INVALID_INPUT");

        assertHeldBy(hold, a1);
    }

    @Test
    @DisplayName("선점 해제 오류는 6.6 표의 순서로 처음 걸린 하나만 돌려준다(401 → 400 → 404)")
    void releaseErrorsFollowTheTableOrder() throws Exception {
        long bobsHold = createHold(bob, performance, a1);

        // 401이 400·404보다 먼저: 토큰 없이 정수가 아닌 holdId, 토큰 없이 다른 사용자의 선점
        expectProblem(mockMvc.perform(delete("/api/holds/{holdId}", "abc")), 401, "AUTH_REQUIRED");
        expectProblem(mockMvc.perform(delete("/api/holds/{holdId}", bobsHold)), 401, "AUTH_REQUIRED");
        // 400이 404보다 먼저: 다른 사용자의 선점(404)을 16진수로 쓴 값
        expectProblem(mockMvc.perform(delete("/api/holds/{holdId}", hex(bobsHold)).with(AuthRequests.asUser(alice))),
                400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(release(alice, bobsHold)), 404, "NOT_FOUND");

        assertHeldBy(bobsHold, a1);
    }

    // ---- R6 ----

    @Test
    @DisplayName("R6: 유효한 토큰이면 선점 조회·해제를 쓸 수 있다(같은 요청에 토큰이 없으면 401 AUTH_REQUIRED)")
    void validTokenCanQueryAndRelease() throws Exception {
        long hold = createHold(alice, performance, a1);
        String bearer = "Bearer " + TestTokens.sign(jwtEncoder, alice, clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        expectProblem(mockMvc.perform(get("/api/holds/{holdId}", hold)), 401, "AUTH_REQUIRED");
        mockMvc.perform(get("/api/holds/{holdId}", hold).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hold));

        expectProblem(mockMvc.perform(delete("/api/holds/{holdId}", hold)), 401, "AUTH_REQUIRED");
        assertHeldBy(hold, a1);
        mockMvc.perform(delete("/api/holds/{holdId}", hold).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        assertThat(holds.holdStatus(hold)).isEqualTo("RELEASED");
        assertAvailable(a1);
    }

    /**
     * 결정 0090: holdId는 공연 상세의 performanceId와 같은 규칙이다. 내 선점의 id를 정수가 아닌 표기로 바꿔 보낸다.
     * 느슨하게 해석하면 내 선점(조회 200, 해제 204)이 되거나 다른 id(404)가 된다.
     */
    static Stream<Arguments> nonIntegerHoldIds() {
        return Stream.of(
                notation("문자", id -> "abc"),
                notation("소수 {id}.0", id -> id + ".0"),
                notation("long 범위 밖", id -> "9223372036854775808"),
                notation("16진수 0x{id}", id -> "0x" + Long.toHexString(id)),
                notation("16진수 #{id}", id -> "#" + Long.toHexString(id)),
                notation("앞 공백 ' {id}'", id -> " " + id),
                notation("뒤 공백 '{id} '", id -> id + " "),
                notation("전각 숫자", id -> fullWidth(id)));
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

    /** 선점이 ACTIVE로 남아 있고 좌석들을 잡고 있다. */
    private void assertHeldBy(long hold, long... seats) {
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
        for (long seat : seats) {
            assertThat(holds.seatStatus(seat)).isEqualTo("HELD");
            assertThat(holds.seatHoldId(seat)).isEqualTo(hold);
        }
    }

    private static void expectProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private static String hex(long id) {
        return "0x" + Long.toHexString(id);
    }

    private static String fullWidth(long id) {
        StringBuilder written = new StringBuilder();
        for (char digit : Long.toString(id).toCharArray()) {
            written.append((char) ('０' + (digit - '0')));
        }
        return written.toString();
    }

    private static Arguments notation(String name, LongFunction<String> write) {
        return Arguments.of(name, write);
    }

    /** JsonPath는 작은 정수를 Integer로 읽는다. 테스트 데이터의 id는 작으므로 int로 바꿔 비교한다. */
    private static int asInt(long id) {
        return Math.toIntExact(id);
    }
}
