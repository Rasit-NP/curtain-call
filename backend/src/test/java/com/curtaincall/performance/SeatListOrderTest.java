package com.curtaincall.performance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.curtaincall.support.HoldRequests;
import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestPerformances;

/**
 * 좌석 목록 순서(spec 5: row는 코드 포인트 순서, number는 숫자 순서)를 공연 상세와 선점 응답에서 함께 확인한다.
 *
 * <p>공연 상세는 DB가 정렬하고(seat_row COLLATE "C": UTF-8 바이트 순서 = 코드 포인트 순서, V2 마이그레이션), 선점 응답은 잠근 좌석을
 * 자바에서 정렬한다(SeatOrder). 두 정렬은 서로 다른 곳에 있어 한쪽만 바뀌면 같은 좌석의 순서가 응답마다 달라진다.
 * DB 기본 정렬(테스트 컨테이너의 en_US.utf8)은 대소문자를 먼저 구분하지 않아 'a'를 'B' 앞에 둔다. V2의 COLLATE "C"를 잠시 빼면
 * 이 테스트가 실패하는 것을 #45에서 확인했다. 자바 비교 자체(보충 문자)는 단위 테스트 SeatOrderTest가 본다.
 */
class SeatListOrderTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");
    private static final String BMP_LAST = "�";
    private static final String SUPPLEMENTARY = "🎭"; // U+1F3AD

    @Autowired
    private TestPerformances performances;

    @Test
    @DisplayName("공연 상세(DB 정렬)와 선점 응답(자바 정렬)의 좌석은 row 코드 포인트 순서이고 서로 같다(대소문자가 섞인 row, 보충 문자)")
    void detailAndHoldOrderRowsByCodePoint() throws Exception {
        long user = testUsers.create("orderer", "orderer-password");
        long venue = performances.createVenue("정렬홀");
        long performance = performances.createPerformance("정렬 공연", venue, STARTS_AT, 30000);
        // 넣는 순서와 격자 위치를 응답 순서와 다르게 둔다.
        long smallA = performances.addSeat(performance, "a", 1, 1, 1);
        long supplementary = performances.addSeat(performance, SUPPLEMENTARY, 1, 1, 2);
        long bigB = performances.addSeat(performance, "B", 1, 1, 3);
        long bmpLast = performances.addSeat(performance, BMP_LAST, 1, 1, 4);
        long bigA = performances.addSeat(performance, "A", 1, 1, 5);

        // 코드 포인트: A(U+0041) < B(U+0042) < a(U+0061) < U+FFFD < U+1F3AD
        mockMvc.perform(get("/api/performances/{performanceId}", performance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].row", contains("A", "B", "a", BMP_LAST, SUPPLEMENTARY)))
                .andExpect(jsonPath("$.seats[*].id", contains(
                        asInt(bigA), asInt(bigB), asInt(smallA), asInt(bmpLast), asInt(supplementary))));

        // 요청의 좌석 순서와 상관없이 같은 순서다. 공연 상세에서 'A'를 뺀 순서와 같다.
        mockMvc.perform(HoldRequests.hold(user, performance, supplementary, smallA, bmpLast, bigB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats[*].row", contains("B", "a", BMP_LAST, SUPPLEMENTARY)))
                .andExpect(jsonPath("$.seats[*].id", contains(
                        asInt(bigB), asInt(smallA), asInt(bmpLast), asInt(supplementary))));
    }

    /** JsonPath는 작은 정수를 Integer로 읽는다. 테스트 데이터의 id는 작으므로 int로 바꿔 비교한다. */
    private static int asInt(long id) {
        return Math.toIntExact(id);
    }
}
