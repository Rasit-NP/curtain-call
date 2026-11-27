package com.curtaincall.reservation.api;

import static com.curtaincall.support.HoldRequests.hold;
import static com.curtaincall.support.HoldRequests.holdId;
import static com.curtaincall.support.PaymentRequests.pay;
import static com.curtaincall.support.PaymentRequests.reservationId;
import static com.curtaincall.support.PaymentRequests.reservations;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.curtaincall.support.HoldRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestPerformances;

/**
 * 내 예매 API(spec 6.8). 예매는 결제 API로 만든다(선점 → 결제 승인). 토큰이 없으면 401인 것은 {@code PaymentApiTest}의 R6 테스트와
 * {@code AuthenticationTest}가 본다.
 */
class ReservationApiTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");

    @Autowired
    private TestPerformances performances;

    private long alice;
    private long bob;
    private long venue;
    private long hamlet;
    private long seagull;

    @BeforeEach
    void createUsersAndPerformances() {
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
        venue = performances.createVenue("예매홀");
        hamlet = performances.createPerformance("작은 방의 햄릿", venue, STARTS_AT, 30000);
        seagull = performances.createPerformance("갈매기", venue, STARTS_AT.plusSeconds(86400), 25000);
    }

    @Test
    @DisplayName("내 예매만 최신순(reservedAt 내림차순, 같으면 id 내림차순)으로 돌려준다")
    void myReservationsLatestFirst() throws Exception {
        long h1 = performances.addSeat(hamlet, "A", 1, 1, 1);
        long h2 = performances.addSeat(hamlet, "A", 2, 1, 2);
        long h3 = performances.addSeat(hamlet, "A", 3, 1, 3);
        long h4 = performances.addSeat(hamlet, "A", 4, 1, 4);
        long s1 = performances.addSeat(seagull, "A", 1, 1, 1);
        long s2 = performances.addSeat(seagull, "A", 2, 1, 2);

        clock.setInstant(Instant.parse("2026-12-01T10:01:00Z"));
        long first = reserve(alice, hamlet, h1, h2);
        // 같은 시각의 두 예매: id가 큰 것이 앞이다. 먼저 만든 예매(second)의 id가 작다.
        clock.setInstant(Instant.parse("2026-12-01T10:03:00Z"));
        long second = reserve(alice, seagull, s1);
        long third = reserve(alice, hamlet, h3);
        // 다른 사용자의 예매, 거절된 결제, 해제한 선점은 내 예매가 아니다.
        clock.setInstant(Instant.parse("2026-12-01T10:05:00Z"));
        reserve(bob, seagull, s2);
        long declined = createHold(alice, hamlet, h4);
        paymentGateway.declineNext();
        mockMvc.perform(pay(alice, declined)).andExpect(status().isPaymentRequired());
        mockMvc.perform(HoldRequests.release(alice, declined)).andExpect(status().isNoContent());

        mockMvc.perform(reservations(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(asInt(third), asInt(second), asInt(first))))
                .andExpect(jsonPath("$[*].reservedAt",
                        contains("2026-12-01T10:03:00Z", "2026-12-01T10:03:00Z", "2026-12-01T10:01:00Z")))
                .andExpect(jsonPath("$[*].performance.title", contains("작은 방의 햄릿", "갈매기", "작은 방의 햄릿")))
                .andExpect(jsonPath("$[*].amount", contains(30000, 25000, 60000)));
        mockMvc.perform(reservations(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].seats[*].id", contains(asInt(s2))));
    }

    @Test
    @DisplayName("내 예매의 모양은 Reservation이다: performance는 PerformanceSummary, seats는 row·number 오름차순의 SeatRef")
    void reservationShape() throws Exception {
        // 요청 순서, id 순서와 다른 표시 순서(A2, A10, B1)를 만든다.
        long b1 = performances.addSeat(hamlet, "B", 1, 2, 1);
        long a10 = performances.addSeat(hamlet, "A", 10, 1, 10);
        long a2 = performances.addSeat(hamlet, "A", 2, 1, 2);
        clock.setInstant(Instant.parse("2026-12-01T10:03:00Z"));
        long reservation = reserve(alice, hamlet, b1, a10, a2);

        mockMvc.perform(reservations(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].*", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value(reservation))
                .andExpect(jsonPath("$[0].performance.*", hasSize(5)))
                .andExpect(jsonPath("$[0].performance.id").value(hamlet))
                .andExpect(jsonPath("$[0].performance.title").value("작은 방의 햄릿"))
                .andExpect(jsonPath("$[0].performance.venue").value("예매홀"))
                .andExpect(jsonPath("$[0].performance.startsAt").value("2026-12-20T10:00:00Z"))
                .andExpect(jsonPath("$[0].performance.price").value(30000))
                .andExpect(jsonPath("$[0].seats[*].id", contains(asInt(a2), asInt(a10), asInt(b1))))
                .andExpect(jsonPath("$[0].seats[*].row", contains("A", "A", "B")))
                .andExpect(jsonPath("$[0].seats[*].number", contains(2, 10, 1)))
                .andExpect(jsonPath("$[0].seats[0].*", hasSize(3)))
                .andExpect(jsonPath("$[0].amount").value(90000))
                .andExpect(jsonPath("$[0].reservedAt").value("2026-12-01T10:03:00Z"));
    }

    @Test
    @DisplayName("예매가 없으면 []다(유효한 선점만 있어도 예매가 아니다)")
    void noReservationsIsEmptyArray() throws Exception {
        long seat = performances.addSeat(hamlet, "A", 1, 1, 1);
        createHold(alice, hamlet, seat);

        mockMvc.perform(reservations(alice))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    /** 선점하고 결제해 예매를 만들고 예매 id를 돌려준다. */
    private long reserve(long userId, long performanceId, long... seatIds) throws Exception {
        long holdId = createHold(userId, performanceId, seatIds);
        return reservationId(mockMvc.perform(pay(userId, holdId)).andExpect(status().isCreated()).andReturn());
    }

    private long createHold(long userId, long performanceId, long... seatIds) throws Exception {
        return holdId(mockMvc.perform(hold(userId, performanceId, seatIds)).andExpect(status().isCreated()).andReturn());
    }

    /** JsonPath는 작은 정수를 Integer로 읽는다. 테스트 데이터의 id는 작으므로 int로 바꿔 비교한다. */
    private static int asInt(long id) {
        return Math.toIntExact(id);
    }
}
