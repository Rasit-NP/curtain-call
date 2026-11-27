package com.curtaincall.hold;

import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static org.assertj.core.api.Assertions.assertThat;

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
 * 동시에 오는 선점 요청(R1, R2, spec 2.4). {@link CountDownLatch}로 여러 스레드를 동시에 출발시키고, 응답(상태와 code)과 DB 최종 상태
 * (선점 행, 좌석의 저장 상태와 holdId, 선점의 좌석 목록)를 함께 검증한다.
 *
 * <p>경합은 확률적으로 일어난다. 요청 두 개가 부딪히는 경우는 구현의 잠금을 되돌렸을 때 실패를 놓치지 않도록 공연을 새로 만들어 여러 번
 * 반복한다. 반복마다 공연이 다르므로 앞 반복의 선점이 다음 반복의 보유 좌석 수(R2)에 들어가지 않는다.
 */
class HoldConcurrencyTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");
    private static final int PRICE = 30000;
    private static final int PAIR_REPETITIONS = 20;
    private static final int DEADLOCK_REPETITIONS = 50;

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
        int count = requests.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (RequestBuilder request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
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
        return results.stream().filter(result -> result.getResponse().getStatus() == 409).map(HoldRequests::code).toList();
    }

    private static List<MvcResult> created(List<MvcResult> results) {
        return results.stream().filter(result -> result.getResponse().getStatus() == 201).toList();
    }
}
