package com.curtaincall.payment;

import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static com.curtaincall.support.PaymentRequests.pay;
import static com.curtaincall.support.PaymentRequests.reservationId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.curtaincall.payment.infra.FakePaymentGateway;
import com.curtaincall.support.DatabaseLocks;
import com.curtaincall.support.DatabaseLocks.Observed;
import com.curtaincall.support.HoldRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPayments;
import com.curtaincall.support.TestPerformances;

/**
 * 결제와 동시에 오는 요청(R2, R4, spec 2.4, 결정 0100). 결제는 요청한 사용자 행 → 선점 행 → 좌석(id 오름차순)을 잠그고, 현재 시각은 모든 잠금 뒤에
 * 읽으며, 모의 결제가 늦게 끝나도 잠금을 쥔 채 결제를 마친다.
 *
 * <p>순서는 모의 결제의 멈추기(래치)로 만든다: 결제가 판정을 통과하고 모의 결제에서 멈춘 동안 다른 요청을 보내고, 그 요청이 DB 행 잠금을 기다리기
 * 시작한 것을 PostgreSQL에서 확인한 뒤({@link DatabaseLocks}) 결제를 풀어 준다. 시간(sleep)에 기대지 않으므로 한 번에 같은 순서가 만들어진다.
 * 구현의 잠금을 되돌리면 나중 요청은 기다리지 않고 먼저 끝나 결과가 달라진다. 응답과 DB 최종 상태를 함께 검증한다.
 */
class PaymentConcurrencyTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");
    private static final int PRICE = 30000;
    /** R3: 선점은 5분간 유효하다. 구현의 상수를 쓰지 않고 명세 값을 그대로 쓴다. */
    private static final Duration VALIDITY = Duration.ofMinutes(5);
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final int ORDER_REPETITIONS = 5;
    private static final int DEADLOCK_REPETITIONS = 50;

    @Autowired
    private TestPerformances performances;

    @Autowired
    private TestHolds holds;

    @Autowired
    private TestPayments payments;

    @Autowired
    private DatabaseLocks locks;

    private ExecutorService executor;
    private long venue;
    private long alice;
    private long bob;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
        venue = performances.createVenue("결제동시홀");
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
    }

    @AfterEach
    void tearDown() {
        // 실패한 테스트가 멈춘 결제를 남기지 않게 푼다.
        paymentGateway.reset();
        executor.shutdownNow();
    }

    /**
     * 결정 0100 '결제가 사용자 행을 잠가야 하는 이유'의 재현. 결제가 만료 1초 전에 판정을 통과하고 모의 결제에서 멈춘 동안 만료 시각이 되고, 같은 사용자가
     * 같은 공연의 다른 4석을 선점한다. 선점은 결제가 쥔 사용자 행을 기다렸다가, 결제가 커밋한 예매 2석을 세어 409다.
     * 결제가 사용자 행을 잠그지 않으면 선점은 기다리지 않고, 만료로 보이는 선점(2석)도 커밋 전인 예매도 세지 않아 4석을 잡는다(예매 2석 + 선점 4석 = 6석).
     */
    @Test
    @DisplayName("R2: 2석 선점의 결제가 만료 시각을 넘겨 끝나는 동안 같은 사용자가 같은 공연에 4석을 새로 선점하면 409 HOLD_LIMIT_EXCEEDED이고, "
            + "결제가 끝난 뒤 보유 좌석 수는 예매 2석이다(모의 결제를 래치로 멈춤)")
    void paymentAndNewHoldOfTheSameUserStayWithinLimit() throws Exception {
        long performance = newPerformance(0);
        long[] seats = addSeats(performance, 6);
        long paying = createHold(alice, performance, seats[0], seats[1]);
        Instant expiresAt = clock.instant().plus(VALIDITY);
        clock.setInstant(expiresAt.minusSeconds(1));

        FakePaymentGateway.Pause pause = paymentGateway.pauseNext();
        Future<MvcResult> payment = submit(pay(alice, paying));
        assertThat(pause.awaitEntered(WAIT)).as("결제가 판정을 통과해 모의 결제에서 멈춤").isTrue();
        clock.setInstant(expiresAt);
        Future<MvcResult> newHold = submit(hold(alice, performance, seats[2], seats[3], seats[4], seats[5]));
        Observed observed = locks.awaitLockWaitersOrDone(1, WAIT, newHold);
        pause.resume();

        assertThat(status(payment)).as("결제").isEqualTo(201);
        assertThat(status(newHold)).as("선점(" + observed + ")").isEqualTo(409);
        assertThat(code(newHold)).isEqualTo("HOLD_LIMIT_EXCEEDED");
        assertThat(observed).as("선점은 결제가 쥔 사용자 행을 기다린다").isEqualTo(Observed.WAITING_FOR_LOCK);
        assertThat(holds.holdStatus(paying)).isEqualTo("PAID");
        assertThat(holds.countHolds(performance)).isEqualTo(1);
        assertThat(payments.reservationSeatIds(reservationId(payment.get()))).containsExactly(seats[0], seats[1]);
        for (int i = 2; i < 6; i++) {
            assertThat(holds.seatStatus(seats[i])).as("좌석 " + seats[i]).isEqualTo("AVAILABLE");
        }
    }

    /**
     * 결제가 판정을 통과하고 모의 결제에서 멈춘 동안 만료 시각이 지나고, 다른 사용자가 그 좌석을 선점한다. 선점은 결제가 쥔 좌석 잠금을 기다렸다가
     * 결제가 커밋한 SOLD를 읽어 409다. 결제가 좌석을 잠그지 않으면 선점은 만료로 보이는 좌석을 바로 잡고, 결제가 그 좌석을 SOLD로 덮어쓴다.
     */
    @Test
    @DisplayName("R4: 결제를 처리하는 동안 만료 시각이 지나도 다른 사용자가 그 좌석을 선점하지 못한다")
    void seatsUnderPaymentCannotBeTakenAfterExpiry() throws Exception {
        long performance = newPerformance(0);
        long[] seats = addSeats(performance, 2);
        long paying = createHold(alice, performance, seats);
        Instant expiresAt = clock.instant().plus(VALIDITY);
        clock.setInstant(expiresAt.minusSeconds(1));

        FakePaymentGateway.Pause pause = paymentGateway.pauseNext();
        Future<MvcResult> payment = submit(pay(alice, paying));
        assertThat(pause.awaitEntered(WAIT)).as("결제가 판정을 통과해 모의 결제에서 멈춤").isTrue();
        clock.setInstant(expiresAt.plusSeconds(30));
        Future<MvcResult> takeover = submit(hold(bob, performance, seats[1], seats[0]));
        Observed observed = locks.awaitLockWaitersOrDone(1, WAIT, takeover);
        pause.resume();

        assertThat(status(payment)).as("결제").isEqualTo(201);
        assertThat(status(takeover)).as("다른 사용자의 선점(" + observed + ")").isEqualTo(409);
        assertThat(code(takeover)).isEqualTo("SEAT_ALREADY_HELD");
        assertThat(observed).as("다른 사용자의 선점은 결제가 쥔 좌석 잠금을 기다린다").isEqualTo(Observed.WAITING_FOR_LOCK);
        assertThat(holds.countHolds(performance)).isEqualTo(1);
        for (long seat : seats) {
            assertThat(holds.seatStatus(seat)).as("좌석 " + seat).isEqualTo("SOLD");
            assertThat(holds.seatHoldId(seat)).as("좌석 " + seat).isNull();
        }
        assertThat(payments.reservationSeatIds(reservationId(payment.get()))).containsExactly(seats[0], seats[1]);
    }

    /**
     * 현재 시각은 모든 잠금 뒤에 읽는다(spec 2.4, 결정 0100). 같은 사용자의 앞 결제가 사용자 행을 쥔 채 모의 결제에서 멈춘 동안 다음 결제가 와서
     * 사용자 행을 기다리고, 그사이 그 선점의 만료 시각이 지난다. 다음 결제는 잠근 뒤의 시각으로 판정해 409이고 모의 결제를 부르지 않는다.
     * 잠그기 전에 읽은 시각으로 판정하면 만료 전 시각으로 판정을 통과해 201이 된다.
     */
    @Test
    @DisplayName("R4: 결제가 앞 요청의 잠금을 기다리는 동안 만료 시각이 지나면, 잠근 뒤의 현재 시각으로 판정해 409 HOLD_EXPIRED이고 모의 결제를 부르지 않는다")
    void paymentJudgesExpiryAfterAcquiringLocks() throws Exception {
        long performance = newPerformance(0);
        long[] seats = addSeats(performance, 2);
        long expiring = createHold(alice, performance, seats[0]);
        Instant expiresAt = clock.instant().plus(VALIDITY);
        clock.advance(Duration.ofMinutes(1));
        long later = createHold(alice, performance, seats[1]);
        clock.setInstant(expiresAt.minusSeconds(1));

        FakePaymentGateway.Pause pause = paymentGateway.pauseNext();
        Future<MvcResult> first = submit(pay(alice, later));
        assertThat(pause.awaitEntered(WAIT)).as("앞 결제가 모의 결제에서 멈춤").isTrue();
        Future<MvcResult> second = submit(pay(alice, expiring));
        Observed observed = locks.awaitLockWaitersOrDone(1, WAIT, second);
        clock.setInstant(expiresAt);
        pause.resume();

        assertThat(status(first)).as("앞 결제").isEqualTo(201);
        assertThat(status(second)).as("다음 결제(" + observed + ")").isEqualTo(409);
        assertThat(code(second)).isEqualTo("HOLD_EXPIRED");
        assertThat(observed).as("다음 결제는 앞 결제가 쥔 사용자 행을 기다린다").isEqualTo(Observed.WAITING_FOR_LOCK);
        assertThat(paymentGateway.callCount()).isEqualTo(1);
        assertThat(holds.holdStatus(expiring)).isEqualTo("ACTIVE");
        assertThat(payments.countReservationsOf(expiring)).isZero();
        assertThat(holds.seatStatus(seats[0])).isEqualTo("HELD");
    }

    /**
     * 같은 선점을 다른 키로 두 번 결제한다. 먼저 모의 결제에 들어간 쪽이 멈춘 동안 다른 쪽은 잠금(같은 사용자라 사용자 행)을 기다리고,
     * 앞 결제가 커밋한 PAID를 읽어 404다. 어느 쪽이 먼저 들어가는지 섞이도록 공연을 새로 만들어 반복한다.
     */
    @Test
    @DisplayName("같은 선점을 서로 다른 키로 동시에 결제하면 하나만 201이고 나머지는 404 NOT_FOUND이며 예매는 하나다")
    void samePaymentWithDifferentKeys() throws Exception {
        for (int round = 1; round <= ORDER_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long[] seats = addSeats(performance, 2);
            long hold = createHold(alice, performance, seats);
            paymentGateway.reset();

            FakePaymentGateway.Pause pause = paymentGateway.pauseNext();
            List<Future<MvcResult>> both = submitTogether(List.of(pay(alice, hold), pay(alice, hold)));
            assertThat(pause.awaitEntered(WAIT)).isTrue();
            Observed observed = locks.awaitLockWaitersOrDone(1, WAIT, both.get(0), both.get(1));
            pause.resume();

            String at = "반복 " + round + "(" + observed + ")";
            assertThat(List.of(status(both.get(0)), status(both.get(1)))).as(at).containsExactlyInAnyOrder(201, 404);
            assertThat(payments.countReservationsOf(hold)).as(at).isEqualTo(1);
            assertThat(payments.paymentStatusesOf(hold)).as(at).containsExactly("SUCCEEDED");
            assertThat(paymentGateway.callCount()).as(at).isEqualTo(1);
            assertThat(holds.holdStatus(hold)).as(at).isEqualTo("PAID");
            assertThat(observed).as(at).isEqualTo(Observed.WAITING_FOR_LOCK);
        }
    }

    /**
     * 결제와 해제는 선점 행을 잠그므로 같은 선점에 함께 오면 하나씩 처리된다(spec 2.4). 해제는 사용자 행을 잠그지 않으므로 결제가 쥔 선점 행을 기다린다.
     * 결제가 승인되면 해제는 PAID를 읽어 404이고, 거절되면 그대로인 선점을 해제해 204다.
     */
    @Test
    @DisplayName("같은 선점에 결제와 해제가 동시에 오면 하나씩 처리된다. 결제가 먼저 승인되면 해제는 404 NOT_FOUND, 결제가 먼저 거절되면 해제는 204다")
    void paymentAndReleaseOfTheSameHold() throws Exception {
        long performance = newPerformance(0);
        long[] seats = addSeats(performance, 4);

        long approvedHold = createHold(alice, performance, seats[0], seats[1]);
        FakePaymentGateway.Pause approving = paymentGateway.pauseNext();
        Future<MvcResult> approved = submit(pay(alice, approvedHold));
        assertThat(approving.awaitEntered(WAIT)).isTrue();
        Future<MvcResult> releaseAfterApproval = submit(HoldRequests.release(alice, approvedHold));
        Observed afterApproval = locks.awaitLockWaitersOrDone(1, WAIT, releaseAfterApproval);
        approving.resume();

        assertThat(status(approved)).as("결제").isEqualTo(201);
        assertThat(status(releaseAfterApproval)).as("승인 뒤 해제(" + afterApproval + ")").isEqualTo(404);
        assertThat(code(releaseAfterApproval)).isEqualTo("NOT_FOUND");
        assertThat(afterApproval).isEqualTo(Observed.WAITING_FOR_LOCK);
        assertThat(holds.holdStatus(approvedHold)).isEqualTo("PAID");
        assertThat(holds.seatStatus(seats[0])).isEqualTo("SOLD");
        assertThat(holds.seatStatus(seats[1])).isEqualTo("SOLD");

        long declinedHold = createHold(alice, performance, seats[2], seats[3]);
        paymentGateway.declineNext();
        FakePaymentGateway.Pause declining = paymentGateway.pauseNext();
        Future<MvcResult> declined = submit(pay(alice, declinedHold));
        assertThat(declining.awaitEntered(WAIT)).isTrue();
        Future<MvcResult> releaseAfterDecline = submit(HoldRequests.release(alice, declinedHold));
        Observed afterDecline = locks.awaitLockWaitersOrDone(1, WAIT, releaseAfterDecline);
        declining.resume();

        assertThat(status(declined)).as("결제").isEqualTo(402);
        assertThat(status(releaseAfterDecline)).as("거절 뒤 해제(" + afterDecline + ")").isEqualTo(204);
        assertThat(afterDecline).isEqualTo(Observed.WAITING_FOR_LOCK);
        assertThat(holds.holdStatus(declinedHold)).isEqualTo("RELEASED");
        assertThat(payments.paymentStatusesOf(declinedHold)).containsExactly("FAILED");
        assertThat(payments.countReservationsOf(declinedHold)).isZero();
        for (long seat : new long[] {seats[2], seats[3]}) {
            assertThat(holds.seatStatus(seat)).as("좌석 " + seat).isEqualTo("AVAILABLE");
            assertThat(holds.seatHoldId(seat)).as("좌석 " + seat).isNull();
        }
    }

    /**
     * 결제와, 같은 좌석을 반대 순서로 담은 다른 사용자의 선점이 함께 온다. 둘 다 좌석을 id 오름차순으로 잠그므로 교착 상태가 생기지 않는다.
     * 결제는 만료 1초 전이라 판정을 통과하고, 선점은 어느 쪽이 먼저든 유효한 선점의 좌석이나 판매된 좌석을 만나 409다.
     *
     * <p>좌석을 잠그기 전 단계는 결제(사용자 행, 선점 행)가 선점 생성(사용자 행, 공연, 좌석 수, 보유 좌석 수)보다 짧아, 같은 순간에 보내면 결제가 거의
     * 언제나 먼저 좌석을 모두 잠근다. 그래서 반복마다 결제를 0~10ms 늦게 보내 좌석을 잠그는 단계가 겹치게 한다(결정 0121과 같은 방법).
     */
    @Test
    @DisplayName("결제와 다른 사용자의 같은 좌석 선점(반대 순서)이 동시에 와도 결제는 201, 선점은 409 SEAT_ALREADY_HELD이고 "
            + "교착 상태로 500이 나지 않는다(50회 반복, 결제를 0~10ms 늦게 보내 순서를 섞음)")
    void paymentAndOtherUsersHoldNeverDeadlock() throws Exception {
        for (int round = 1; round <= DEADLOCK_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long[] seats = addSeats(performance, 4);
            long paying = createHold(alice, performance, seats);
            clock.setInstant(clock.instant().plus(VALIDITY).minusSeconds(1));
            long paymentDelayMillis = (round % 6) * 2L;

            List<Future<MvcResult>> results = submitTogether(List.of(
                    pay(alice, paying),
                    hold(bob, performance, seats[3], seats[2], seats[1], seats[0])), new long[] {paymentDelayMillis, 0});

            String at = "반복 " + round + "(결제 " + paymentDelayMillis + "ms 늦게)";
            assertThat(status(results.get(0))).as(at + " 결제").isEqualTo(201);
            assertThat(status(results.get(1))).as(at + " 선점").isEqualTo(409);
            assertThat(code(results.get(1))).as(at).isEqualTo("SEAT_ALREADY_HELD");
            assertThat(holds.countHolds(performance)).as(at).isEqualTo(1);
            for (long seat : seats) {
                assertThat(holds.seatStatus(seat)).as(at + ", 좌석 " + seat).isEqualTo("SOLD");
            }
            // 다음 반복의 선점은 지금 시각에 만든다.
        }
    }

    private long createHold(long userId, long performanceId, long... seatIds) throws Exception {
        MvcResult result = mockMvc.perform(hold(userId, performanceId, seatIds)).andReturn();
        assertThat(result.getResponse().getStatus()).as("준비 선점").isEqualTo(201);
        return holdId(result);
    }

    private long newPerformance(int round) {
        return performances.createPerformance("결제 동시 공연 " + round, venue, STARTS_AT, PRICE);
    }

    private long[] addSeats(long performance, int count) {
        return IntStream.rangeClosed(1, count).mapToLong(i -> performances.addSeat(performance, "A", i, 1, i)).toArray();
    }

    private Future<MvcResult> submit(RequestBuilder request) {
        return executor.submit(() -> mockMvc.perform(request).andReturn());
    }

    private List<Future<MvcResult>> submitTogether(List<? extends RequestBuilder> requests) throws InterruptedException {
        return submitTogether(requests, new long[requests.size()]);
    }

    /** 요청을 스레드 하나씩에 맡겨 동시에 출발시킨다. 요청마다 출발 신호 뒤 delaysMillis만큼 기다렸다가 보낸다. */
    private List<Future<MvcResult>> submitTogether(List<? extends RequestBuilder> requests, long[] delaysMillis)
            throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            RequestBuilder request = requests.get(i);
            long delayMillis = delaysMillis[i];
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                return mockMvc.perform(request).andReturn();
            }));
        }
        assertThat(ready.await(WAIT.toSeconds(), TimeUnit.SECONDS)).as("모든 요청 스레드가 준비됨").isTrue();
        start.countDown();
        return futures;
    }

    private static int status(Future<MvcResult> future) throws Exception {
        return future.get(WAIT.toSeconds(), TimeUnit.SECONDS).getResponse().getStatus();
    }

    private static String code(Future<MvcResult> future) throws Exception {
        return HoldRequests.code(future.get(WAIT.toSeconds(), TimeUnit.SECONDS));
    }
}
