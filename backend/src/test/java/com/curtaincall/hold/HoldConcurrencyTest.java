package com.curtaincall.hold;

import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.curtaincall.support.HoldRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPerformances;

/**
 * 동시에 오는 선점·해제 요청(R1, R2, spec 2.4). {@link CountDownLatch}로 여러 스레드를 동시에 출발시키고, 응답(상태와 code)과 DB 최종 상태
 * (선점 행과 저장 상태, 좌석의 저장 상태와 holdId, 선점의 좌석 목록)를 함께 검증한다.
 *
 * <p>경합은 확률적으로 일어난다. 요청 두 개가 부딪히는 경우는 구현의 잠금을 되돌렸을 때 실패를 놓치지 않도록 공연을 새로 만들어 여러 번
 * 반복한다. 반복마다 공연이 다르므로 앞 반복의 선점이 다음 반복의 보유 좌석 수(R2)에 들어가지 않는다.
 */
class HoldConcurrencyTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");
    private static final int PRICE = 30000;
    private static final int PAIR_REPETITIONS = 20;
    private static final int DEADLOCK_REPETITIONS = 50;
    private static final int RELEASE_REQUESTS = 5;
    /** R3: 선점은 5분간 유효하다. 구현의 상수를 쓰지 않고 명세 값을 그대로 쓴다. */
    private static final Duration VALIDITY = Duration.ofMinutes(5);

    @Autowired
    private TestPerformances performances;

    @Autowired
    private TestHolds holds;

    private long venue;

    @BeforeEach
    void createVenue() {
        venue = performances.createVenue("동시홀");
    }

    @Test
    @DisplayName("R1: 같은 좌석에 선점 요청 10개가 동시에 오면 하나만 201이고 나머지는 409 SEAT_ALREADY_HELD다")
    void tenConcurrentRequestsForTheSameSeat() throws Exception {
        long performance = newPerformance(0);
        long seat = performances.addSeat(performance, "A", 1, 1, 1);
        List<Long> users = createUsers("same-seat", 10);

        List<MvcResult> results = concurrently(users.stream().map(user -> hold(user, performance, seat)).toList());

        assertThat(statuses(results)).containsOnly(201, 409).filteredOn(s -> s == 201).hasSize(1);
        assertThat(conflictCodes(results)).hasSize(9).containsOnly("SEAT_ALREADY_HELD");
        long winner = holdId(created(results).getFirst());
        assertThat(holds.countHolds()).isEqualTo(1);
        assertThat(holds.seatHoldId(seat)).isEqualTo(winner);
        assertThat(holds.seatIdsOf(winner)).containsExactly(seat);
    }

    @Test
    @DisplayName("R1: 겹치는 묶음(A1·A2와 A2·A3)을 동시에 선점하면 하나만 성공하고, 실패한 쪽 좌석은 하나도 잡히지 않는다")
    void overlappingBundles() throws Exception {
        List<Long> users = createUsers("overlap", 2);
        for (int round = 1; round <= PAIR_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long a1 = performances.addSeat(performance, "A", 1, 1, 1);
            long a2 = performances.addSeat(performance, "A", 2, 1, 2);
            long a3 = performances.addSeat(performance, "A", 3, 1, 3);

            List<MvcResult> results = concurrently(List.of(
                    hold(users.get(0), performance, a1, a2),
                    hold(users.get(1), performance, a2, a3)));

            String at = "반복 " + round;
            assertThat(statuses(results)).as(at).containsExactlyInAnyOrder(201, 409);
            assertThat(conflictCodes(results)).as(at).containsExactly("SEAT_ALREADY_HELD");
            boolean firstWon = results.get(0).getResponse().getStatus() == 201;
            long winner = holdId(created(results).getFirst());
            List<Long> winnerSeats = firstWon ? List.of(a1, a2) : List.of(a2, a3);
            long loserOnlySeat = firstWon ? a3 : a1;
            assertThat(holds.countHolds(performance)).as(at).isEqualTo(1);
            assertThat(holds.seatIdsOf(winner)).as(at).containsExactlyElementsOf(winnerSeats);
            assertThat(holds.heldSeatIds(performance)).as(at).containsExactlyElementsOf(winnerSeats);
            assertThat(holds.seatStatus(loserOnlySeat)).as(at).isEqualTo("AVAILABLE");
            assertThat(holds.seatHoldId(loserOnlySeat)).as(at).isNull();
        }
    }

    /**
     * 같은 두 좌석을 반대 순서로 담은 묶음끼리는 좌석을 요청 순서대로 잠그면 서로 상대가 잠근 좌석을 기다려 교착 상태가 된다. PostgreSQL이
     * 그중 하나를 끊으면 500이 된다. 좌석을 언제나 id 오름차순으로 잠그면 생기지 않는다(spec 2.4). 한 번에 걸리지 않을 수 있어 반복한다.
     */
    @Test
    @DisplayName("R1: 같은 두 좌석을 반대 순서로 담은 묶음([A1, A2]와 [A2, A1])을 동시에 선점하기를 50회 반복해도 매번 하나만 성공하고 500이 나지 않는다")
    void reversedBundlesNeverDeadlock() throws Exception {
        List<Long> users = createUsers("reversed", 2);
        for (int round = 1; round <= DEADLOCK_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long a1 = performances.addSeat(performance, "A", 1, 1, 1);
            long a2 = performances.addSeat(performance, "A", 2, 1, 2);

            List<MvcResult> results = concurrently(List.of(
                    hold(users.get(0), performance, a1, a2),
                    hold(users.get(1), performance, a2, a1)));

            String at = "반복 " + round;
            assertThat(statuses(results)).as(at).containsExactlyInAnyOrder(201, 409);
            assertThat(conflictCodes(results)).as(at).containsExactly("SEAT_ALREADY_HELD");
            long winner = holdId(created(results).getFirst());
            assertThat(holds.countHolds(performance)).as(at).isEqualTo(1);
            assertThat(holds.seatHoldId(a1)).as(at).isEqualTo(winner);
            assertThat(holds.seatHoldId(a2)).as(at).isEqualTo(winner);
        }
    }

    @Test
    @DisplayName("R1: 서로 다른 좌석을 동시에 선점하면 모두 성공한다")
    void disjointBundlesAllSucceed() throws Exception {
        long performance = newPerformance(0);
        List<Long> users = createUsers("disjoint", 4);
        List<long[]> bundles = IntStream.range(0, 4)
                .mapToObj(i -> new long[] {
                        performances.addSeat(performance, "A", 2 * i + 1, 1, 2 * i + 1),
                        performances.addSeat(performance, "A", 2 * i + 2, 1, 2 * i + 2)})
                .toList();

        List<MvcResult> results = concurrently(IntStream.range(0, 4)
                .mapToObj(i -> hold(users.get(i), performance, bundles.get(i)))
                .toList());

        assertThat(statuses(results)).containsOnly(201).hasSize(4);
        assertThat(holds.countHolds(performance)).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            long hold = holdId(results.get(i));
            assertThat(holds.userIdOf(hold)).isEqualTo(users.get(i));
            for (long seat : bundles.get(i)) {
                assertThat(holds.seatHoldId(seat)).isEqualTo(hold);
            }
        }
    }

    @Test
    @DisplayName("R2: 같은 사용자가 서로 다른 좌석 3석 묶음 두 개를 동시에 선점하면 하나만 201이고 나머지는 409 HOLD_LIMIT_EXCEEDED다")
    void sameUserConcurrentBundlesStayWithinLimit() throws Exception {
        long user = createUsers("same-user", 1).getFirst();
        for (int round = 1; round <= PAIR_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long[] first = new long[3];
            long[] second = new long[3];
            for (int i = 0; i < 3; i++) {
                first[i] = performances.addSeat(performance, "A", i + 1, 1, i + 1);
                second[i] = performances.addSeat(performance, "B", i + 1, 2, i + 1);
            }

            List<MvcResult> results = concurrently(List.of(
                    hold(user, performance, first),
                    hold(user, performance, second)));

            String at = "반복 " + round;
            assertThat(statuses(results)).as(at).containsExactlyInAnyOrder(201, 409);
            assertThat(conflictCodes(results)).as(at).containsExactly("HOLD_LIMIT_EXCEEDED");
            long winner = holdId(created(results).getFirst());
            assertThat(holds.countHolds(performance)).as(at).isEqualTo(1);
            assertThat(holds.heldSeatIds(performance)).as(at).hasSize(3)
                    .containsExactlyElementsOf(holds.seatIdsOf(winner));
        }
    }

    /**
     * 해제는 선점 행을 먼저 잠근다(spec 2.4, 결정 0100). 잠그지 않으면 여러 요청이 모두 ACTIVE를 읽고 좌석을 기다렸다가 저마다 204가 된다.
     * 한 번에 걸리지 않을 수 있어 반복한다.
     */
    @Test
    @DisplayName("같은 선점에 해제 요청 5개가 동시에 오면 하나만 204이고 나머지는 404 NOT_FOUND다(20회 반복, 선점은 RELEASED, 좌석은 AVAILABLE)")
    void concurrentReleasesOfTheSameHold() throws Exception {
        long user = createUsers("release", 1).getFirst();
        for (int round = 1; round <= PAIR_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long a1 = performances.addSeat(performance, "A", 1, 1, 1);
            long a2 = performances.addSeat(performance, "A", 2, 1, 2);
            long hold = createHold(user, performance, a1, a2);

            List<MvcResult> results = concurrently(IntStream.range(0, RELEASE_REQUESTS)
                    .mapToObj(i -> HoldRequests.release(user, hold))
                    .toList());

            String at = "반복 " + round;
            assertThat(statuses(results)).as(at).containsOnly(204, 404).filteredOn(s -> s == 204).hasSize(1);
            assertThat(codesOf(results, 404)).as(at).hasSize(RELEASE_REQUESTS - 1).containsOnly("NOT_FOUND");
            assertThat(holds.holdStatus(hold)).as(at).isEqualTo("RELEASED");
            assertThat(holds.heldSeatIds(performance)).as(at).isEmpty();
            assertThat(holds.seatHoldId(a1)).as(at).isNull();
            assertThat(holds.seatHoldId(a2)).as(at).isNull();
        }
    }

    /**
     * 만료된 선점의 해제와, 그 좌석을 덮어쓰는 다른 사용자의 선점이 함께 온다. 둘 다 좌석을 잠그므로 어느 쪽이 먼저든 둘 다 성공해야 한다.
     * <ul>
     *   <li>해제가 먼저면 좌석을 AVAILABLE로 되돌리고, 선점은 그 뒤의 값(AVAILABLE)으로 판정해 잡는다.</li>
     *   <li>선점이 먼저면 해제는 좌석을 잠근 뒤 holdId가 새 선점인 것을 보고 건드리지 않는다. 잠그지 않고 읽은 값으로 되돌리면 새 선점의 좌석이 풀린다.</li>
     *   <li>좌석을 서로 다른 순서로 잠그면 교착 상태가 되어 PostgreSQL이 하나를 끊고 500이 된다. 둘 다 id 오름차순으로 잠가 생기지 않는다.</li>
     * </ul>
     * 교착 상태가 생길 수 있게 덮어쓰는 좌석을 4석으로 하고, 한 번에 걸리지 않을 수 있어 반복한다.
     *
     * <p>둘을 같은 순간에 보내면 좌석을 잠그기 전 단계가 짧은 해제가 거의 언제나 먼저 좌석을 잡는다(처음 쓴 테스트에서 holdId로 거르는 조건을 빼도
     * 50회 모두 통과했다). 그래서 반복마다 해제를 0~10ms 늦게 보내, 선점이 먼저 좌석을 잡거나 커밋한 뒤에 해제가 오는 순서도 생기게 한다.
     * 기다리는 시간은 순서를 섞을 뿐이고, 검증은 어느 순서에서도 같다.
     */
    @Test
    @DisplayName("만료된 선점의 해제와 다른 사용자의 같은 좌석 선점이 동시에 와도 둘 다 성공하고, 새 선점의 좌석은 풀리지 않으며 500이 나지 않는다(50회 반복)")
    void releaseOfExpiredHoldRacingWithNewHoldOfTheSameSeats() throws Exception {
        List<Long> users = createUsers("expired-race", 2);
        long owner = users.get(0);
        long other = users.get(1);
        for (int round = 1; round <= DEADLOCK_REPETITIONS; round++) {
            long performance = newPerformance(round);
            long[] seats = IntStream.rangeClosed(1, 4)
                    .mapToLong(i -> performances.addSeat(performance, "A", i, 1, i))
                    .toArray();
            long expired = createHold(owner, performance, seats);
            clock.advance(VALIDITY);
            long[] reversed = {seats[3], seats[2], seats[1], seats[0]};

            long releaseDelayMillis = (round % 6) * 2L;

            List<MvcResult> results = concurrently(List.of(
                    HoldRequests.release(owner, expired),
                    hold(other, performance, reversed)), new long[] {releaseDelayMillis, 0});

            String at = "반복 " + round + "(해제 " + releaseDelayMillis + "ms 늦게)";
            assertThat(statuses(results)).as(at).containsExactly(204, 201);
            long renewed = holdId(results.get(1));
            assertThat(holds.holdStatus(expired)).as(at).isEqualTo("RELEASED");
            assertThat(holds.holdStatus(renewed)).as(at).isEqualTo("ACTIVE");
            assertThat(holds.userIdOf(renewed)).as(at).isEqualTo(other);
            assertThat(holds.heldSeatIds(performance)).as(at).containsExactly(seats[0], seats[1], seats[2], seats[3]);
            for (long seat : seats) {
                assertThat(holds.seatHoldId(seat)).as(at + ", 좌석 " + seat).isEqualTo(renewed);
            }
        }
    }

    private long createHold(long userId, long performanceId, long... seatIds) throws Exception {
        MvcResult result = mockMvc.perform(hold(userId, performanceId, seatIds)).andReturn();
        assertThat(result.getResponse().getStatus()).as("준비 선점").isEqualTo(201);
        return holdId(result);
    }

    private long newPerformance(int round) {
        return performances.createPerformance("동시 공연 " + round, venue, STARTS_AT, PRICE);
    }

    private List<Long> createUsers(String prefix, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> testUsers.create(prefix + "-" + i, "password-" + i))
                .toList();
    }

    /**
     * 요청을 스레드 하나씩에 맡겨 동시에 출발시키고, 요청 순서대로 결과를 돌려준다. 모든 스레드가 준비될 때까지 기다린 뒤 한 번에 연다.
     * 요청 수는 DB 연결 풀 크기(기본 10) 이하로 둔다.
     */
    private List<MvcResult> concurrently(List<? extends RequestBuilder> requests) throws Exception {
        return concurrently(requests, new long[requests.size()]);
    }

    /** {@link #concurrently(List)}와 같고, 요청마다 출발 신호 뒤 delaysMillis만큼 기다렸다가 보낸다(요청이 겹치는 순서를 바꿀 때). */
    private List<MvcResult> concurrently(List<? extends RequestBuilder> requests, long[] delaysMillis) throws Exception {
        int count = requests.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
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
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("모든 요청 스레드가 준비됨").isTrue();
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Integer> statuses(List<MvcResult> results) {
        return results.stream().map(result -> result.getResponse().getStatus()).toList();
    }

    private static List<String> conflictCodes(List<MvcResult> results) {
        return codesOf(results, 409);
    }

    private static List<String> codesOf(List<MvcResult> results, int status) {
        return results.stream().filter(result -> result.getResponse().getStatus() == status).map(HoldRequests::code).toList();
    }

    private static List<MvcResult> created(List<MvcResult> results) {
        return results.stream().filter(result -> result.getResponse().getStatus() == 201).toList();
    }
}
