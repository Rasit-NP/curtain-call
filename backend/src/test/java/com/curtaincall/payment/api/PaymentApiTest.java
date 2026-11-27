package com.curtaincall.payment.api;

import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static com.curtaincall.support.PaymentRequests.IDEMPOTENCY_KEY;
import static com.curtaincall.support.PaymentRequests.pay;
import static com.curtaincall.support.PaymentRequests.paymentId;
import static com.curtaincall.support.PaymentRequests.reservationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.curtaincall.support.AuthRequests;
import com.curtaincall.support.HoldRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.PaymentRequests;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPayments;
import com.curtaincall.support.TestPerformances;
import com.curtaincall.support.TestTokens;

/**
 * 결제 API(spec 6.7)와 R4·R5(헤더 검증)·R6. 동시에 오는 요청은 {@code PaymentConcurrencyTest}가 다룬다. 멱등 기록(같은 키의 결과 저장·재사용)은
 * #56에서 더한다. 여기서는 결제마다 새 키(UUID)를 쓴다.
 *
 * <p>선점은 API({@code POST /api/holds})로 만들고, 응답과 함께 DB 최종 상태(선점의 저장 상태, 좌석의 저장 상태와 holdId, 예매·결제 행)와
 * 모의 결제의 호출 횟수를 본다. 결제하지 않은 요청은 "예매·결제가 생기지 않고 선점과 좌석이 그대로"인지 확인한다.
 */
class PaymentApiTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");
    private static final int PRICE = 30000;
    /** R3: 선점은 5분간 유효하다. 구현의 상수를 쓰지 않고 명세 값을 그대로 쓴다. */
    private static final Duration VALIDITY = Duration.ofMinutes(5);

    @Autowired
    private TestPerformances performances;

    @Autowired
    private TestHolds holds;

    @Autowired
    private TestPayments payments;

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

    @BeforeEach
    void createUsersAndSeats() {
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
        venue = performances.createVenue("결제홀");
        performance = performances.createPerformance("결제 공연", venue, STARTS_AT, PRICE);
        a1 = performances.addSeat(performance, "A", 1, 1, 1);
        a2 = performances.addSeat(performance, "A", 2, 1, 2);
        a3 = performances.addSeat(performance, "A", 3, 1, 3);
        a4 = performances.addSeat(performance, "A", 4, 1, 4);
    }

    @Test
    @DisplayName("결제가 승인되면 201 { paymentId, amount, reservation }이고 선점은 PAID, 좌석은 SOLD(holdId 없음)가 되며 예매와 결제 SUCCEEDED가 생긴다. "
            + "금액은 가격 × 좌석 수다")
    void approvedPaymentCreatesReservation() throws Exception {
        // 요청 순서, id 순서와 다른 표시 순서(A2, A10, B1)를 만든다. 예매의 좌석은 row·number 오름차순이다.
        long b1 = performances.addSeat(performance, "B", 1, 2, 1);
        long a10 = performances.addSeat(performance, "A", 10, 1, 10);
        long hold = createHold(alice, b1, a10, a2);
        clock.setInstant(Instant.parse("2026-12-01T10:03:00Z"));

        MvcResult result = mockMvc.perform(pay(alice, hold))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.paymentId").isNumber())
                .andExpect(jsonPath("$.amount").value(PRICE * 3))
                .andExpect(jsonPath("$.reservation.*", hasSize(5)))
                .andExpect(jsonPath("$.reservation.id").isNumber())
                .andExpect(jsonPath("$.reservation.performance.*", hasSize(5)))
                .andExpect(jsonPath("$.reservation.performance.id").value(performance))
                .andExpect(jsonPath("$.reservation.performance.title").value("결제 공연"))
                .andExpect(jsonPath("$.reservation.performance.venue").value("결제홀"))
                .andExpect(jsonPath("$.reservation.performance.startsAt").value("2026-12-20T10:00:00Z"))
                .andExpect(jsonPath("$.reservation.performance.price").value(PRICE))
                .andExpect(jsonPath("$.reservation.seats[*].id", contains(asInt(a2), asInt(a10), asInt(b1))))
                .andExpect(jsonPath("$.reservation.seats[*].row", contains("A", "A", "B")))
                .andExpect(jsonPath("$.reservation.seats[*].number", contains(2, 10, 1)))
                .andExpect(jsonPath("$.reservation.seats[0].*", hasSize(3)))
                .andExpect(jsonPath("$.reservation.amount").value(PRICE * 3))
                .andExpect(jsonPath("$.reservation.reservedAt").value("2026-12-01T10:03:00Z"))
                .andReturn();

        long reservation = reservationId(result);
        long payment = paymentId(result);
        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
        for (long seat : new long[] {a2, a10, b1}) {
            assertThat(holds.seatStatus(seat)).as("좌석 " + seat).isEqualTo("SOLD");
            assertThat(holds.seatHoldId(seat)).as("좌석 " + seat).isNull();
        }
        assertThat(payments.reservationSeatIds(reservation)).containsExactlyInAnyOrder(a2, a10, b1);
        assertThat(payments.reservation(reservation)).containsEntry("user_id", alice)
                .containsEntry("performance_id", performance)
                .containsEntry("hold_id", hold)
                .containsEntry("amount", (long) PRICE * 3);
        assertThat(payments.payment(payment)).containsEntry("user_id", alice)
                .containsEntry("hold_id", hold)
                .containsEntry("amount", (long) PRICE * 3)
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("reservation_id", reservation);
        assertThat(paymentGateway.callCount()).isEqualTo(1);
        // 다른 좌석은 그대로다.
        assertAvailable(a1);
    }

    @Test
    @DisplayName("결제가 승인되면 공연 상세에서 그 좌석은 SOLD다")
    void soldSeatsAppearSoldInPerformanceDetail() throws Exception {
        long hold = createHold(alice, a1, a2);

        mockMvc.perform(pay(alice, hold)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].status", contains("SOLD", "SOLD", "AVAILABLE", "AVAILABLE")));
        // 만료 시각이 지나도 판매된 좌석은 SOLD다(만료는 선점에만 있다).
        clock.advance(VALIDITY.plusHours(1));
        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(jsonPath("$.seats[*].status", contains("SOLD", "SOLD", "AVAILABLE", "AVAILABLE")));
    }

    @Test
    @DisplayName("R4: 만료 시각 1초 전의 결제는 201이고 예매가 생긴다")
    void paymentOneSecondBeforeExpiryIsApproved() throws Exception {
        long hold = createHold(alice, a1, a2);
        clock.setInstant(clock.instant().plus(VALIDITY).minusSeconds(1));

        MvcResult result = mockMvc.perform(pay(alice, hold)).andExpect(status().isCreated()).andReturn();

        assertThat(payments.countReservationsOf(hold)).isEqualTo(1);
        assertThat(payments.reservationSeatIds(reservationId(result))).containsExactly(a1, a2);
        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
        assertThat(paymentGateway.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("R4: 만료 시각과 같은 순간의 결제는 409 HOLD_EXPIRED다")
    void paymentAtExpiryIsRejected() throws Exception {
        long hold = createHold(alice, a1, a2);
        clock.setInstant(clock.instant().plus(VALIDITY));

        expectProblem(mockMvc.perform(pay(alice, hold)), 409, "HOLD_EXPIRED");

        assertThat(payments.countReservations()).isZero();
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("R4: 만료된 선점으로 결제하면 예매가 생기지 않고, 좌석이 판매되지 않으며, 모의 결제를 부르지 않는다")
    void expiredHoldIsNotCharged() throws Exception {
        long hold = createHold(alice, a1, a2);
        clock.advance(VALIDITY.plusHours(1));

        expectProblem(mockMvc.perform(pay(alice, hold)), 409, "HOLD_EXPIRED");

        assertThat(paymentGateway.callCount()).isZero();
        assertThat(payments.countReservations()).isZero();
        assertThat(payments.countPayments()).isZero();
        // 만료는 저장하지 않는다(지연 만료). 선점과 좌석의 저장 상태는 그대로다.
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
        assertThat(holds.seatStatus(a1)).isEqualTo("HELD");
        assertThat(holds.seatHoldId(a1)).isEqualTo(hold);
        assertThat(holds.seatHoldId(a2)).isEqualTo(hold);
    }

    @Test
    @DisplayName("R4: 만료 뒤 다른 사용자가 좌석을 선점했으면 원래 사용자의 결제는 409 HOLD_EXPIRED이고 다른 사용자의 선점은 그대로다")
    void paymentAfterSeatWasTakenOverIsRejected() throws Exception {
        long alicesHold = createHold(alice, a1, a2);
        clock.advance(VALIDITY);
        long bobsHold = createHold(bob, a1);

        expectProblem(mockMvc.perform(pay(alice, alicesHold)), 409, "HOLD_EXPIRED");

        assertThat(paymentGateway.callCount()).isZero();
        assertThat(payments.countReservations()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(holds.holdStatus(bobsHold)).isEqualTo("ACTIVE");
        assertThat(holds.seatStatus(a1)).isEqualTo("HELD");
        assertThat(holds.seatHoldId(a1)).isEqualTo(bobsHold);
        assertThat(holds.holdStatus(alicesHold)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("결제가 거절되면 402 PAYMENT_FAILED이고 예매가 생기지 않으며, 선점·좌석·만료 시각은 그대로이고 결제는 FAILED로 남는다")
    void declinedPaymentKeepsHold() throws Exception {
        long hold = createHold(alice, a1, a2);
        Timestamp expiresAt = payments.holdExpiresAt(hold);
        clock.advance(Duration.ofMinutes(2));
        paymentGateway.declineNext();

        mockMvc.perform(pay(alice, hold))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.detail").value("결제가 승인되지 않았습니다."));

        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(payments.countReservations()).isZero();
        assertThat(payments.paymentStatusesOf(hold)).containsExactly("FAILED");
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
        assertThat(payments.holdExpiresAt(hold)).isEqualTo(expiresAt);
        assertThat(holds.seatStatus(a1)).isEqualTo("HELD");
        assertThat(holds.seatHoldId(a1)).isEqualTo(hold);
        assertThat(holds.seatHoldId(a2)).isEqualTo(hold);
        // 선점이 남아 있으므로 조회하면 ACTIVE다.
        mockMvc.perform(HoldRequests.getHold(alice, hold))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("거절된 결제의 기록은 FAILED에 예매 없이 남는다(금액은 가격 × 좌석 수)")
    void declinedPaymentRecord() throws Exception {
        long hold = createHold(alice, a1, a2, a3);
        paymentGateway.declineNext();

        mockMvc.perform(pay(alice, hold)).andExpect(status().isPaymentRequired());

        assertThat(payments.countPayments()).isEqualTo(1);
        long payment = payments.paymentIdsOf(hold).getFirst();
        assertThat(payments.payment(payment)).containsEntry("user_id", alice)
                .containsEntry("amount", (long) PRICE * 3)
                .containsEntry("status", "FAILED")
                .containsEntry("reservation_id", null);
    }

    @Test
    @DisplayName("이미 결제된 선점을 새 키로 결제하면 404 NOT_FOUND이고 예매는 하나다")
    void paidHoldCannotBePaidAgain() throws Exception {
        long hold = createHold(alice, a1, a2);
        mockMvc.perform(pay(alice, hold)).andExpect(status().isCreated());

        expectProblem(mockMvc.perform(pay(alice, hold)), 404, "NOT_FOUND");

        assertThat(payments.countReservationsOf(hold)).isEqualTo(1);
        assertThat(payments.paymentStatusesOf(hold)).containsExactly("SUCCEEDED");
        assertThat(paymentGateway.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 사용자의 선점, 해제된 선점, 없는 선점으로 결제하면 404 NOT_FOUND이고 모의 결제를 부르지 않으며 선점은 그대로다")
    void othersReleasedOrMissingHoldIsNotFound() throws Exception {
        long bobsHold = createHold(bob, a1);
        long released = createHold(alice, a2);
        mockMvc.perform(HoldRequests.release(alice, released)).andExpect(status().isNoContent());
        long missing = Math.max(bobsHold, released) + 1000;

        expectProblem(mockMvc.perform(pay(alice, bobsHold)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(pay(alice, released)), 404, "NOT_FOUND");
        expectProblem(mockMvc.perform(pay(alice, missing)), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.detail").value("선점을 찾을 수 없습니다."));

        assertThat(paymentGateway.callCount()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(payments.countReservations()).isZero();
        assertThat(holds.holdStatus(bobsHold)).isEqualTo("ACTIVE");
        assertThat(holds.seatHoldId(a1)).isEqualTo(bobsHold);
        assertThat(holds.holdStatus(released)).isEqualTo("RELEASED");
        // 주인은 결제할 수 있다(404가 선점이 없어서가 아니라 다른 사용자의 선점이라서다).
        mockMvc.perform(pay(bob, bobsHold)).andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidBodies() {
        // {h}는 내 유효한 선점의 id로 바꾼다. 400이 아니면 그대로 결제될 값이다.
        return Stream.of(
                Arguments.of("빈 객체", "{}"),
                Arguments.of("holdId가 null", "{\"holdId\":null}"),
                Arguments.of("holdId가 0", "{\"holdId\":0}"),
                Arguments.of("holdId가 음수", "{\"holdId\":-1}"),
                Arguments.of("holdId가 소수(잘라 받지 않는다)", "{\"holdId\":{h}.5}"),
                Arguments.of("holdId에 소수점이 있는 수", "{\"holdId\":{h}.0}"),
                Arguments.of("holdId가 지수 표기", "{\"holdId\":{h}e0}"),
                Arguments.of("holdId가 문자열", "{\"holdId\":\"{h}\"}"),
                Arguments.of("holdId가 배열", "{\"holdId\":[{h}]}"),
                Arguments.of("holdId가 long 범위 밖", "{\"holdId\":99999999999999999999}"),
                Arguments.of("JSON이 아님", "{\"holdId\":{h}"),
                Arguments.of("본문 없음", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("holdId가 없거나, 양수가 아니거나, JSON 정수가 아니면(소수·지수 표기·문자열, 결정 0072) 400 INVALID_INPUT이고 결제하지 않는다")
    void invalidBodyIsInvalidInput(String description, String template) throws Exception {
        long hold = createHold(alice, a1);

        expectProblem(mockMvc.perform(post("/api/payments")
                .with(AuthRequests.asUser(alice))
                .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(template.replace("{h}", String.valueOf(hold)))), 400, "INVALID_INPUT");

        assertNothingPaid(hold);
    }

    @Test
    @DisplayName("R5: Idempotency-Key가 없거나 공백뿐이면 400 INVALID_INPUT이다")
    void missingOrBlankKeyIsInvalidInput() throws Exception {
        long hold = createHold(alice, a1);

        expectProblem(mockMvc.perform(payWithoutKey(alice, hold)), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(pay(alice, hold, "")), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(pay(alice, hold, " ")), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(pay(alice, hold, "   ")), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(pay(alice, hold, "\t")), 400, "INVALID_INPUT");

        assertNothingPaid(hold);
    }

    @Test
    @DisplayName("R5: 키 길이 100자는 허용하고 101자는 400 INVALID_INPUT이다")
    void keyLengthBoundary() throws Exception {
        long hold = createHold(alice, a1);

        expectProblem(mockMvc.perform(pay(alice, hold, "k".repeat(101))), 400, "INVALID_INPUT");
        assertNothingPaid(hold);

        mockMvc.perform(pay(alice, hold, "k".repeat(100))).andExpect(status().isCreated());
        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
    }

    static Stream<Arguments> nonVisibleAsciiKeys() {
        return Stream.of(
                Arguments.of("가운데 공백", "a b"),
                Arguments.of("앞 공백", " key"),
                Arguments.of("뒤 공백", "key "),
                Arguments.of("한글", "키"),
                Arguments.of("라틴 문자", "kéy"),
                Arguments.of("제어 문자 DEL", "key\u007F"),
                Arguments.of("유니코드 공백", "key\u3000"),
                // 실제 서버(Tomcat)는 헤더 바이트를 ISO-8859-1로 읽는다. '키'의 UTF-8 바이트(ED 82 A4)는 C1 제어 문자 U+0082가 든 문자열이 된다.
                Arguments.of("한글을 UTF-8로 보낸 값(서버가 읽은 문자열)",
                        new String("키".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonVisibleAsciiKeys")
    @DisplayName("R5: Idempotency-Key에 ASCII 출력 문자가 아닌 문자(공백, 제어 문자, ASCII가 아닌 문자)가 있으면 400 INVALID_INPUT이다(결정 0141)")
    void keyWithNonVisibleAsciiIsInvalidInput(String description, String key) throws Exception {
        long hold = createHold(alice, a1);

        expectProblem(mockMvc.perform(pay(alice, hold, key)), 400, "INVALID_INPUT");

        assertNothingPaid(hold);
    }

    @Test
    @DisplayName("R5: Idempotency-Key 헤더가 두 개 이상이면 400 INVALID_INPUT이다(결정 0141)")
    void twoKeyHeadersAreInvalidInput() throws Exception {
        long hold = createHold(alice, a1);

        expectProblem(mockMvc.perform(payWithoutKey(alice, hold).header(IDEMPOTENCY_KEY, "key-1", "key-2")),
                400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(payWithoutKey(alice, hold).header(IDEMPOTENCY_KEY, "key-1", "key-1")),
                400, "INVALID_INPUT");

        assertNothingPaid(hold);
    }

    @Test
    @DisplayName("결제 오류는 6.7 표의 순서로 처음 걸린 하나만 돌려준다(401 → 400 → 404 → 409 HOLD_EXPIRED → 402 PAYMENT_FAILED, 3번 멱등 키는 #56)")
    void errorsFollowTheTableOrder() throws Exception {
        long alicesExpired = createHold(alice, a1);
        long bobsExpired = createHold(bob, a2);
        clock.advance(VALIDITY);
        long alicesValid = createHold(alice, a3);

        // 401이 400보다 먼저: 토큰 없이 키도 없고 잘못된 본문
        expectProblem(mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{}")),
                401, "AUTH_REQUIRED");
        // 400이 404보다 먼저: 키 없이 다른 사용자의 선점, 키는 있고 양수가 아닌 holdId(없는 선점이기도 하다)
        expectProblem(mockMvc.perform(payWithoutKey(alice, bobsExpired)), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(post("/api/payments")
                .with(AuthRequests.asUser(alice))
                .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":0}")), 400, "INVALID_INPUT");
        // 404가 409보다 먼저: 다른 사용자의 만료된 선점
        expectProblem(mockMvc.perform(pay(alice, bobsExpired)), 404, "NOT_FOUND");
        // 409가 402보다 먼저: 다음 호출을 거절로 지정해도 만료된 선점은 모의 결제를 부르지 않고 409다.
        paymentGateway.declineNext();
        expectProblem(mockMvc.perform(pay(alice, alicesExpired)), 409, "HOLD_EXPIRED");
        assertThat(paymentGateway.callCount()).isZero();
        // 거절 지정이 남아 있어 유효한 선점은 402다.
        expectProblem(mockMvc.perform(pay(alice, alicesValid)), 402, "PAYMENT_FAILED");

        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(payments.countReservations()).isZero();
        assertThat(payments.paymentStatusesOf(alicesValid)).containsExactly("FAILED");
    }

    @Test
    @DisplayName("R6: 유효한 토큰이면 결제·내 예매를 쓸 수 있다(같은 요청에 토큰이 없으면 401 AUTH_REQUIRED)")
    void validTokenCanPayAndListReservations() throws Exception {
        long hold = createHold(alice, a1, a2);
        String token = TestTokens.sign(jwtEncoder, alice, clock.instant(), clock.instant().plus(Duration.ofHours(1)));
        String key = UUID.randomUUID().toString();

        expectProblem(mockMvc.perform(post("/api/payments")
                .header(IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PaymentRequests.body(hold))), 401, "AUTH_REQUIRED");
        expectProblem(mockMvc.perform(get("/api/reservations")), 401, "AUTH_REQUIRED");
        assertNothingPaid(hold);

        MvcResult paid = mockMvc.perform(post("/api/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PaymentRequests.body(hold)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(reservationId(paid)));
        assertThat(payments.reservation(reservationId(paid))).containsEntry("user_id", alice);
    }

    private long createHold(long userId, long... seatIds) throws Exception {
        return holdId(mockMvc.perform(hold(userId, performance, seatIds))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static MockHttpServletRequestBuilder payWithoutKey(long userId, long holdId) {
        return post("/api/payments")
                .with(AuthRequests.asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(PaymentRequests.body(holdId));
    }

    /** 결제하지 않았다: 모의 결제를 부르지 않았고, 예매·결제가 없으며, 선점과 좌석이 그대로다. */
    private void assertNothingPaid(long hold) {
        assertThat(paymentGateway.callCount()).isZero();
        assertThat(payments.countReservations()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
        for (long seat : holds.seatIdsOf(hold)) {
            assertThat(holds.seatStatus(seat)).isEqualTo("HELD");
            assertThat(holds.seatHoldId(seat)).isEqualTo(hold);
        }
    }

    private void assertAvailable(long seat) {
        assertThat(holds.seatStatus(seat)).isEqualTo("AVAILABLE");
        assertThat(holds.seatHoldId(seat)).isNull();
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** JsonPath는 작은 정수를 Integer로 읽는다. 테스트 데이터의 id는 작으므로 int로 바꿔 비교한다. */
    private static int asInt(long id) {
        return Math.toIntExact(id);
    }
}
