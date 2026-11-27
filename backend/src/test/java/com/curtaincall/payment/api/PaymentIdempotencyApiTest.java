package com.curtaincall.payment.api;

import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static com.curtaincall.support.PaymentRequests.IDEMPOTENCY_KEY;
import static com.curtaincall.support.PaymentRequests.pay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

/**
 * 결제의 멱등 키(R5, spec 6.7.1). 같은 사용자가 같은 키로 다시 보내면 다시 처리하지 않고 저장한 응답(상태 코드와 본문)을 그대로 돌려준다.
 * 동시에 오는 같은 키 요청은 {@code PaymentConcurrencyTest}가 다룬다.
 *
 * <p>"다시 처리하지 않았다"는 응답이 처음과 같은 것만으로는 알 수 없으므로, 다시 처리하면 결과가 달라지는 상황(선점이 결제·해제·만료됨, 없던 선점이 생김,
 * 거절 지정이 없음)을 만든 뒤 같은 키로 보낸다. 응답(상태, Content-Type, 본문 문자열)이 처음과 같은지와 함께 모의 결제의 호출 횟수, 예매·결제·멱등 기록 행을 본다.
 */
class PaymentIdempotencyApiTest extends IntegrationTest {

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

    private long alice;
    private long bob;
    private long performance;
    private long a1;
    private long a2;
    private long a3;
    private long a4;

    @BeforeEach
    void createUsersAndSeats() {
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
        long venue = performances.createVenue("멱등홀");
        performance = performances.createPerformance("멱등 공연", venue, STARTS_AT, PRICE);
        a1 = performances.addSeat(performance, "A", 1, 1, 1);
        a2 = performances.addSeat(performance, "A", 2, 1, 2);
        a3 = performances.addSeat(performance, "A", 3, 1, 3);
        a4 = performances.addSeat(performance, "A", 4, 1, 4);
    }

    @Test
    @DisplayName("R5: 같은 키로 두 번 결제하면 두 번 다 같은 201 응답이고 예매는 하나다(선점이 이미 결제돼 다시 처리하면 404일 때도)")
    void sameKeyTwiceReturnsTheSameApproval() throws Exception {
        long hold = createHold(alice, a1, a2);
        String key = "pay-twice";

        MvcResult first = payWith(alice, hold, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        clock.advance(Duration.ofMinutes(1));
        MvcResult second = payWith(alice, hold, key);
        MvcResult third = payWith(alice, hold, key);

        assertSameResponse(second, first);
        assertSameResponse(third, first);
        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(payments.countReservations()).isEqualTo(1);
        assertThat(payments.countPayments()).isEqualTo(1);
        assertThat(payments.countIdempotencyRecords()).isEqualTo(1);
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", hold).containsEntry("response_status", 201);
        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("R5: 402로 거절된 결제를 같은 키로 다시 요청하면 모의 결제를 다시 부르지 않고 같은 402를 돌려주며, 선점은 유지된다")
    void declinedPaymentIsNotRetriedWithTheSameKey() throws Exception {
        long hold = createHold(alice, a1, a2);
        Timestamp expiresAt = payments.holdExpiresAt(hold);
        String key = "declined";
        paymentGateway.declineNext();

        MvcResult first = payWith(alice, hold, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(402);
        // 거절 지정은 한 번뿐이라 다시 처리하면 승인된다.
        MvcResult second = payWith(alice, hold, key);

        assertSameResponse(second, first);
        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(payments.paymentStatusesOf(hold)).containsExactly("FAILED");
        assertThat(payments.countReservations()).isZero();
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", hold).containsEntry("response_status", 402);
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
        assertThat(payments.holdExpiresAt(hold)).isEqualTo(expiresAt);
        for (long seat : new long[] {a1, a2}) {
            assertThat(holds.seatStatus(seat)).as("좌석 " + seat).isEqualTo("HELD");
            assertThat(holds.seatHoldId(seat)).as("좌석 " + seat).isEqualTo(hold);
        }
    }

    @Test
    @DisplayName("R5: 402가 저장된 키로 다시 요청하면 그 사이 선점이 해제되거나 만료됐어도 같은 402다(다시 처리하면 404·409)")
    void storedDeclineSurvivesReleaseAndExpiry() throws Exception {
        long released = createHold(alice, a1);
        long expiring = createHold(alice, a2);
        paymentGateway.declineNext();
        MvcResult declinedThenReleased = payWith(alice, released, "declined-then-released");
        paymentGateway.declineNext();
        MvcResult declinedThenExpired = payWith(alice, expiring, "declined-then-expired");
        assertThat(declinedThenReleased.getResponse().getStatus()).isEqualTo(402);
        assertThat(declinedThenExpired.getResponse().getStatus()).isEqualTo(402);

        mockMvc.perform(HoldRequests.release(alice, released)).andExpect(status().isNoContent());
        clock.advance(VALIDITY);

        assertSameResponse(payWith(alice, released, "declined-then-released"), declinedThenReleased);
        assertSameResponse(payWith(alice, expiring, "declined-then-expired"), declinedThenExpired);
        assertThat(paymentGateway.callCount()).isEqualTo(2);
        assertThat(payments.countPayments()).isEqualTo(2);
        assertThat(holds.holdStatus(released)).isEqualTo("RELEASED");
        assertThat(holds.holdStatus(expiring)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("R5: 결제가 거절된 뒤 새 키로 다시 결제하면 승인될 수 있다. 거절된 키는 그 뒤에도 같은 402다")
    void newKeyAfterDeclineCanBeApproved() throws Exception {
        long hold = createHold(alice, a1, a2);
        paymentGateway.declineNext();
        MvcResult declined = payWith(alice, hold, "first-try");
        assertThat(declined.getResponse().getStatus()).isEqualTo(402);

        MvcResult approved = payWith(alice, hold, "second-try");

        assertThat(approved.getResponse().getStatus()).isEqualTo(201);
        assertThat(paymentGateway.callCount()).isEqualTo(2);
        assertThat(payments.paymentStatusesOf(hold)).containsExactly("FAILED", "SUCCEEDED");
        assertThat(payments.countReservationsOf(hold)).isEqualTo(1);
        assertThat(holds.holdStatus(hold)).isEqualTo("PAID");
        assertThat(payments.idempotencyRecord(alice, "first-try")).containsEntry("response_status", 402);
        assertThat(payments.idempotencyRecord(alice, "second-try")).containsEntry("response_status", 201);
        // 같은 키는 계속 같은 402다(결정 0005). 선점이 결제된 뒤라 다시 처리하면 404다.
        assertSameResponse(payWith(alice, hold, "first-try"), declined);
        assertThat(paymentGateway.callCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("R5: 이미 처리한 키로 다른 선점을 결제하면 400 INVALID_INPUT이고 아무것도 처리하지 않는다")
    void sameKeyForAnotherHoldIsInvalidInput() throws Exception {
        long paid = createHold(alice, a1);
        long other = createHold(alice, a2, a3);
        String key = "used-key";
        MvcResult first = payWith(alice, paid, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        expectProblem(mockMvc.perform(pay(alice, other, key)), 400, "INVALID_INPUT");

        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(payments.countPayments()).isEqualTo(1);
        assertThat(payments.countReservations()).isEqualTo(1);
        assertThat(payments.countIdempotencyRecords()).isEqualTo(1);
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", paid).containsEntry("response_status", 201);
        assertThat(holds.holdStatus(other)).isEqualTo("ACTIVE");
        for (long seat : new long[] {a2, a3}) {
            assertThat(holds.seatStatus(seat)).as("좌석 " + seat).isEqualTo("HELD");
            assertThat(holds.seatHoldId(seat)).as("좌석 " + seat).isEqualTo(other);
        }
        // 기록은 그대로다: 원래 선점에는 처음 응답을 돌려주고, 다른 선점은 새 키로 결제할 수 있다.
        assertSameResponse(payWith(alice, paid, key), first);
        mockMvc.perform(pay(alice, other, "new-key")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("R5: 다른 사용자가 같은 키를 쓰면 서로 영향 없이 따로 처리된다(다른 사용자의 저장된 응답을 받지 않는다)")
    void keysAreSeparatedByUser() throws Exception {
        long alicesHold = createHold(alice, a1);
        long bobsHold = createHold(bob, a2);
        String key = "shared-key";

        MvcResult alicesPayment = payWith(alice, alicesHold, key);
        MvcResult bobsPayment = payWith(bob, bobsHold, key);

        assertThat(alicesPayment.getResponse().getStatus()).isEqualTo(201);
        assertThat(bobsPayment.getResponse().getStatus()).isEqualTo(201);
        assertThat(PaymentRequests.reservationId(bobsPayment)).isNotEqualTo(PaymentRequests.reservationId(alicesPayment));
        assertThat(paymentGateway.callCount()).isEqualTo(2);
        assertThat(payments.countReservations()).isEqualTo(2);
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", alicesHold).containsEntry("response_status", 201);
        assertThat(payments.idempotencyRecord(bob, key)).containsEntry("hold_id", bobsHold).containsEntry("response_status", 201);
        assertSameResponse(payWith(alice, alicesHold, key), alicesPayment);
        assertSameResponse(payWith(bob, bobsHold, key), bobsPayment);

        // 다른 사용자의 기록과 키·선점이 모두 같아도 그 응답을 받지 않고, 자기 요청으로 처리된다(다른 사용자의 선점이라 404).
        long alicesOther = createHold(alice, a3);
        MvcResult alicesOtherPayment = payWith(alice, alicesOther, "other-key");
        assertThat(alicesOtherPayment.getResponse().getStatus()).isEqualTo(201);
        expectProblem(mockMvc.perform(pay(bob, alicesOther, "other-key")), 404, "NOT_FOUND");
        assertThat(payments.idempotencyRecord(bob, "other-key")).containsEntry("hold_id", alicesOther).containsEntry("response_status", 404);
        assertSameResponse(payWith(alice, alicesOther, "other-key"), alicesOtherPayment);
        assertThat(paymentGateway.callCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("R5: 404로 끝난 요청도 저장해, 같은 키로 다시 보내면 같은 404이고(없던 선점이 그 id로 생겨도) 다른 선점에는 쓸 수 없다(400)")
    void notFoundIsStored() throws Exception {
        long existing = createHold(alice, a1);
        long nextHoldId = existing + 1;
        String key = "not-found";

        MvcResult first = payWith(alice, nextHoldId, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(404);
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", nextHoldId).containsEntry("response_status", 404);

        long created = createHold(alice, a2);
        assertThat(created).as("준비: 없던 선점 id로 선점이 생김").isEqualTo(nextHoldId);
        assertSameResponse(payWith(alice, created, key), first);
        expectProblem(mockMvc.perform(pay(alice, existing, key)), 400, "INVALID_INPUT");

        assertThat(paymentGateway.callCount()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(holds.holdStatus(created)).isEqualTo("ACTIVE");
        assertThat(holds.holdStatus(existing)).isEqualTo("ACTIVE");
        // 다시 처리했다면 결제됐을 선점이다.
        mockMvc.perform(pay(alice, created, "fresh-key")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("R5: 409로 끝난 요청도 저장해, 같은 키로 다시 보내면 같은 409이고 다른 선점에는 쓸 수 없다(400)")
    void holdExpiredIsStored() throws Exception {
        long expired = createHold(alice, a1);
        clock.advance(VALIDITY);
        long valid = createHold(alice, a2);
        String key = "expired";

        MvcResult first = payWith(alice, expired, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(409);
        MvcResult second = payWith(alice, expired, key);
        expectProblem(mockMvc.perform(pay(alice, valid, key)), 400, "INVALID_INPUT");

        assertSameResponse(second, first);
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", expired).containsEntry("response_status", 409);
        assertThat(paymentGateway.callCount()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(holds.holdStatus(valid)).isEqualTo("ACTIVE");
        assertThat(holds.seatHoldId(a2)).isEqualTo(valid);
        mockMvc.perform(pay(alice, valid, "fresh-key")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("R5: 검증 실패(400)로 끝난 요청은 저장하지 않아 같은 키를 다시 쓸 수 있다")
    void invalidRequestIsNotStored() throws Exception {
        long hold = createHold(alice, a1);
        String key = "after-invalid";

        expectProblem(mockMvc.perform(payWithBody(alice, key, "{\"holdId\":0}")), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(payWithBody(alice, key, "{}")), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(payWithBody(alice, key, "{\"holdId\":\"" + hold + "\"}")), 400, "INVALID_INPUT");
        assertThat(payments.countIdempotencyRecords()).isZero();

        mockMvc.perform(pay(alice, hold, key)).andExpect(status().isCreated());

        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", hold).containsEntry("response_status", 201);
        assertThat(paymentGateway.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("R5: 예상하지 못한 오류(500)로 끝난 요청은 저장하지 않아(되돌려짐) 같은 키로 다시 보내면 처음 온 요청처럼 처리한다")
    void unexpectedErrorIsNotStored() throws Exception {
        long hold = createHold(alice, a1, a2);
        String key = "after-error";
        paymentGateway.failNext();

        expectProblem(mockMvc.perform(pay(alice, hold, key)), 500, "INTERNAL_ERROR");

        assertThat(payments.countIdempotencyRecords()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(payments.countReservations()).isZero();
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");

        mockMvc.perform(pay(alice, hold, key)).andExpect(status().isCreated());

        assertThat(paymentGateway.callCount()).isEqualTo(2);
        assertThat(payments.idempotencyRecord(alice, key)).containsEntry("hold_id", hold).containsEntry("response_status", 201);
        assertThat(payments.countReservationsOf(hold)).isEqualTo(1);
    }

    @Test
    @DisplayName("R5: 멱등 키 확인(오류 표 3번)은 요청 검증(2번) 뒤, 선점 확인(4번) 앞이다")
    void idempotencyCheckSitsBetweenValidationAndHoldCheck() throws Exception {
        long paid = createHold(alice, a1);
        long bobsHold = createHold(bob, a2);
        String key = "ordered";
        MvcResult first = payWith(alice, paid, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        // 2번이 3번보다 먼저: 저장된 키·같은 선점이라도 요청 검증에 걸리면 저장된 201이 아니라 400이다.
        expectProblem(mockMvc.perform(payWithBody(alice, key, "{\"holdId\":\"" + paid + "\"}")), 400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(post("/api/payments")
                .with(AuthRequests.asUser(alice))
                .header(IDEMPOTENCY_KEY, key, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PaymentRequests.body(paid))), 400, "INVALID_INPUT");
        // 3번이 4번보다 먼저: 결제된 선점(4번이면 404)이라도 저장된 201이고, 다른 사용자의 선점(4번이면 404)에 쓰면 400이다.
        assertSameResponse(payWith(alice, paid, key), first);
        expectProblem(mockMvc.perform(pay(alice, bobsHold, key)), 400, "INVALID_INPUT");

        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(payments.countIdempotencyRecords()).isEqualTo(1);
        assertThat(holds.holdStatus(bobsHold)).isEqualTo("ACTIVE");
    }

    /**
     * 토큰은 서명·만료만 검증하므로 토큰의 사용자가 DB에 없을 수 있다(사용자 삭제 기능이 없어 정상 흐름에서는 생기지 않는다). 결정 0071대로 따로 처리하지 않는다.
     * 멱등 기록은 사용자를 외래 키로 저장하므로 기록을 잡을 때 외래 키가 막아 500이고, 아무것도 남지 않는다.
     */
    @Test
    @DisplayName("토큰의 사용자가 DB에 없으면 따로 처리하지 않아 결제는 500 INTERNAL_ERROR이고 멱등 기록·결제가 남지 않는다(결정 0071)")
    void missingTokenUserIsNotHandled() throws Exception {
        long hold = createHold(alice, a1);
        long ghost = Math.max(alice, bob) + 1000;

        expectProblem(mockMvc.perform(pay(ghost, hold, "ghost-key")), 500, "INTERNAL_ERROR");

        assertThat(payments.countIdempotencyRecords()).isZero();
        assertThat(payments.countPayments()).isZero();
        assertThat(paymentGateway.callCount()).isZero();
        assertThat(holds.holdStatus(hold)).isEqualTo("ACTIVE");
    }

    private long createHold(long userId, long... seatIds) throws Exception {
        return holdId(mockMvc.perform(hold(userId, performance, seatIds))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private MvcResult payWith(long userId, long holdId, String key) throws Exception {
        return mockMvc.perform(pay(userId, holdId, key)).andReturn();
    }

    /** 본문을 그대로 보내는 결제 요청(키는 하나). */
    private static MockHttpServletRequestBuilder payWithBody(long userId, String key, String body) {
        return post("/api/payments")
                .with(AuthRequests.asUser(userId))
                .header(IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    /** 같은 응답: 상태 코드, Content-Type, 본문 문자열(UTF-8)이 모두 같다. */
    private static void assertSameResponse(MvcResult actual, MvcResult expected) throws Exception {
        assertThat(actual.getResponse().getStatus()).as("상태 코드").isEqualTo(expected.getResponse().getStatus());
        assertThat(actual.getResponse().getContentType()).as("Content-Type").isEqualTo(expected.getResponse().getContentType());
        assertThat(actual.getResponse().getContentAsString(StandardCharsets.UTF_8)).as("본문")
                .isEqualTo(expected.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }
}
