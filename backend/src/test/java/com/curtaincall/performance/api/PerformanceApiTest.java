package com.curtaincall.performance.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.json.JsonCompareMode;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestPerformances;

/**
 * 공연 목록·상세 API(spec 6.2, 6.3)와 공개 경로(R6). 테스트 데이터는 테스트마다 직접 만든다(시드에 기대지 않는다).
 *
 * <p>이 단계에는 선점과 예매가 없어 좌석 status는 저장 상태를 그대로 쓴다. 만료 반영(spec 2.2 '좌석의 API 상태')은
 * 선점 생성 Issue(3단계)에서 한다.
 */
class PerformanceApiTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");

    @Autowired
    private TestPerformances performances;

    @Test
    @DisplayName("R6: 공연 목록과 공연 상세는 토큰 없이 200이다")
    void listAndDetailArePublic() throws Exception {
        long venue = performances.createVenue("공개홀");
        long performance = performances.createPerformance("공개 공연", venue, STARTS_AT, 30000);
        performances.addSeat(performance, "A", 1, 1, 1);

        mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(asInt(performance))));
        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(performance))
                .andExpect(jsonPath("$.seats", hasSize(1)));
    }

    @Test
    @DisplayName("공연 목록은 PerformanceSummary(id, title, venue, startsAt, price)이고 venue는 공연장 이름이다")
    void listItemShape() throws Exception {
        long venue = performances.createVenue("대학로 시험홀");
        long performance = performances.createPerformance("작은 방의 햄릿", venue, STARTS_AT, 30000);

        mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value(performance))
                .andExpect(jsonPath("$[0].title").value("작은 방의 햄릿"))
                .andExpect(jsonPath("$[0].venue").value("대학로 시험홀"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-12-20T10:00:00Z"))
                .andExpect(jsonPath("$[0].price").value(30000));
    }

    @Test
    @DisplayName("공연 목록은 시작 시각 오름차순이고, 시작 시각이 같으면 id 오름차순이다")
    void listIsOrderedByStartsAtThenId() throws Exception {
        long venue = performances.createVenue("정렬홀");
        long otherVenue = performances.createVenue("정렬홀 2관");
        long latest = performances.createPerformance("늦은 공연", venue, STARTS_AT.plusSeconds(3600), 30000);
        long sameFirst = performances.createPerformance("같은 시각 1", venue, STARTS_AT, 30000);
        long earliest = performances.createPerformance("이른 공연", otherVenue, STARTS_AT.minusSeconds(1), 20000);
        long sameSecond = performances.createPerformance("같은 시각 2", otherVenue, STARTS_AT, 30000);
        // id가 작은 쪽을 나중에 고쳐 저장 순서를 id 순서와 다르게 둔다. id 정렬이 빠지면 저장 순서대로 나와 실패한다.
        performances.renamePerformance(sameFirst, "같은 시각 1(고침)");

        mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        asInt(earliest), asInt(sameFirst), asInt(sameSecond), asInt(latest))));
    }

    @Test
    @DisplayName("공연이 없으면 공연 목록은 빈 목록이다")
    void emptyList() throws Exception {
        mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("공연 상세는 PerformanceSummary에 seats를 더하고, 좌석은 id·row·number·gridRow·gridColumn·status다")
    void detailShape() throws Exception {
        long venue = performances.createVenue("상세홀");
        long performance = performances.createPerformance("상세 공연", venue, STARTS_AT, 25000);
        // 통로(격자 2번째 칸) 오른쪽 좌석: number와 gridColumn이 다르다.
        long seat = performances.addSeat(performance, "B", 1, 3, 3);

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.id").value(performance))
                .andExpect(jsonPath("$.title").value("상세 공연"))
                .andExpect(jsonPath("$.venue").value("상세홀"))
                .andExpect(jsonPath("$.startsAt").value("2026-12-20T10:00:00Z"))
                .andExpect(jsonPath("$.price").value(25000))
                .andExpect(jsonPath("$.seats", hasSize(1)))
                .andExpect(jsonPath("$.seats[0].*", hasSize(6)))
                .andExpect(jsonPath("$.seats[0].id").value(seat))
                .andExpect(jsonPath("$.seats[0].row").value("B"))
                .andExpect(jsonPath("$.seats[0].number").value(1))
                .andExpect(jsonPath("$.seats[0].gridRow").value(3))
                .andExpect(jsonPath("$.seats[0].gridColumn").value(3))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("공연 상세의 좌석은 row, number 오름차순이고 number는 숫자 순이다(A-2가 A-10보다 앞)")
    void seatsAreOrderedByRowThenNumber() throws Exception {
        long venue = performances.createVenue("순서홀");
        long performance = performances.createPerformance("순서 공연", venue, STARTS_AT, 30000);
        // 넣는 순서와 격자 위치를 응답 순서와 다르게 둔다(B줄이 무대 쪽 격자 1번째 줄).
        long b10 = performances.addSeat(performance, "B", 10, 1, 10);
        long a2 = performances.addSeat(performance, "A", 2, 2, 2);
        long b1 = performances.addSeat(performance, "B", 1, 1, 1);
        long a10 = performances.addSeat(performance, "A", 10, 2, 10);
        long a1 = performances.addSeat(performance, "A", 1, 2, 1);

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].id", contains(asInt(a1), asInt(a2), asInt(a10), asInt(b1), asInt(b10))));
    }

    @Test
    @DisplayName("공연 상세에는 그 공연의 좌석만 나온다(같은 공연장의 다른 공연 좌석은 나오지 않는다)")
    void detailHasOnlyItsOwnSeats() throws Exception {
        long venue = performances.createVenue("공유홀");
        long performance = performances.createPerformance("첫 회차", venue, STARTS_AT, 30000);
        long other = performances.createPerformance("둘째 회차", venue, STARTS_AT.plusSeconds(86400), 30000);
        long mine = performances.addSeat(performance, "A", 1, 1, 1);
        performances.addSeat(other, "A", 1, 1, 1);
        long mine2 = performances.addSeat(performance, "A", 2, 1, 2);

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].id", contains(asInt(mine), asInt(mine2))));
    }

    /**
     * 선점이 없는 단계라 HELD·SOLD 좌석을 선점·예매 없이 상태만 바꿔 만든다. spec 2.1에서 HELD 좌석은 holdId가 있어야 하므로,
     * 선점 생성 Issue(3단계)가 hold_id와 그 제약을 더하면 이 테스트의 데이터를 만들 수 없게 된다. 그때 이 테스트는 지우지 말고
     * 대응표 R3 행('공연 상세는 만료 시각 1초 전까지 HELD, 만료 시각부터 AVAILABLE')처럼 실제 선점으로 HELD를 만드는 테스트로 바꾼다.
     * 기대값(저장 상태가 응답에 나온다)을 바꾸려고 고치는 것이 아니다.
     */
    @Test
    @DisplayName("이 단계(선점 전)의 좌석 status는 저장 상태 AVAILABLE·HELD·SOLD를 그대로 쓴다")
    void seatStatusIsStoredStatus() throws Exception {
        long venue = performances.createVenue("상태홀");
        long performance = performances.createPerformance("상태 공연", venue, STARTS_AT, 30000);
        performances.addSeat(performance, "A", 1, 1, 1, "AVAILABLE");
        performances.addSeat(performance, "A", 2, 1, 2, "HELD");
        performances.addSeat(performance, "A", 3, 1, 3, "SOLD");

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].status", contains("AVAILABLE", "HELD", "SOLD")));
    }

    @Test
    @DisplayName("좌석이 없는 공연의 상세는 빈 좌석 목록이다")
    void detailWithoutSeats() throws Exception {
        long venue = performances.createVenue("빈홀");
        long performance = performances.createPerformance("좌석 없는 공연", venue, STARTS_AT, 30000);

        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats", hasSize(0)));
    }

    /** spec 3.1: 경로 값의 형식이 틀리면 400. long 범위를 넘는 값도 경로 값을 해석할 수 없으므로 같다. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "1.5", "1e3", "99999999999999999999"})
    @DisplayName("performanceId가 정수가 아니면 400 INVALID_INPUT이다")
    void nonIntegerIdIsInvalidInput(String performanceId) throws Exception {
        mockMvc.perform(get("/api/performances/{performanceId}", performanceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "-1", "999999"})
    @DisplayName("없는 공연의 상세는 404 NOT_FOUND다")
    void unknownPerformanceIsNotFound(String performanceId) throws Exception {
        long venue = performances.createVenue("있는홀");
        performances.createPerformance("있는 공연", venue, STARTS_AT, 30000);

        mockMvc.perform(get("/api/performances/{performanceId}", performanceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /** JsonPath는 작은 정수를 Integer로 읽는다. 테스트 데이터의 id는 작으므로 int로 바꿔 비교한다. */
    private static int asInt(long id) {
        return Math.toIntExact(id);
    }
}
