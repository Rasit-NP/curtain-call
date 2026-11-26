package com.curtaincall.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.SeedFiles;
import com.jayway.jsonpath.JsonPath;

/**
 * 공연장·배치·공연·좌석 시드(spec 2.5). test 프로필은 시드를 넣지 않으므로 테스트마다 테이블을 비운 뒤 {@code db/seed}의 시드
 * 파일을 직접 실행한다(결정 0036과 같은 방식).
 *
 * <p>시드 값은 샘플 데이터다. 제목·개수 같은 값이 아니라 spec 2.5의 최소 조건과 Issue #35가 정한 성질(공연 좌석은 공연장
 * 배치와 같고, 공연장마다 배치가 다르며, 통로나 빈자리가 있는 배치가 있음)만 확인한다.
 */
class PerformanceSeedTest extends IntegrationTest {

    private static final int MIN_PERFORMANCES = 2;
    private static final int MIN_AVAILABLE_SEATS = 5;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void applySeed() throws IOException {
        SeedFiles.apply(dataSource);
    }

    @Test
    @DisplayName("시드를 실제 스키마에서 실행하면 공연이 2개 이상이고, 공연 상세마다 빈 좌석(AVAILABLE)이 5석 이상이다")
    void seedMeetsSpecMinimum() throws Exception {
        String list = mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids).hasSizeGreaterThanOrEqualTo(MIN_PERFORMANCES);

        for (Number id : ids) {
            String detail = mockMvc.perform(get("/api/performances/{performanceId}", id.longValue()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<String> available = JsonPath.read(detail, "$.seats[?(@.status == 'AVAILABLE')].status");
            assertThat(available).as("공연 %s의 빈 좌석 수", id).hasSizeGreaterThanOrEqualTo(MIN_AVAILABLE_SEATS);
        }
    }

    @Test
    @DisplayName("시드의 공연 좌석은 그 공연장의 배치와 같다(row·number·gridRow·gridColumn이 하나도 빠지거나 더해지지 않는다)")
    void performanceSeatsMatchVenueLayout() {
        Integer performancesWithoutSeats = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM performances p
                WHERE NOT EXISTS (SELECT 1 FROM seats s WHERE s.performance_id = p.id)""", Integer.class);
        assertThat(performancesWithoutSeats).isZero();

        // 공연 좌석에만 있는 것과 공연장 배치에만 있는 것을 모두 센다.
        Integer mismatches = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    (SELECT p.id, s.seat_row, s.seat_number, s.grid_row, s.grid_column
                       FROM performances p JOIN seats s ON s.performance_id = p.id
                     EXCEPT
                     SELECT p.id, v.seat_row, v.seat_number, v.grid_row, v.grid_column
                       FROM performances p JOIN venue_seats v ON v.venue_id = p.venue_id)
                    UNION ALL
                    (SELECT p.id, v.seat_row, v.seat_number, v.grid_row, v.grid_column
                       FROM performances p JOIN venue_seats v ON v.venue_id = p.venue_id
                     EXCEPT
                     SELECT p.id, s.seat_row, s.seat_number, s.grid_row, s.grid_column
                       FROM performances p JOIN seats s ON s.performance_id = p.id)
                ) AS diff""", Integer.class);
        assertThat(mismatches).isZero();
    }

    @Test
    @DisplayName("시드에는 배치가 서로 다른 공연장이 2개 이상 있고, 격자 안에 좌석이 없는 칸(통로나 빈자리)이 있는 배치가 있다")
    void venueLayoutsDifferAndHaveGaps() {
        Integer distinctLayouts = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT layout) FROM (
                    SELECT string_agg(seat_row || '-' || seat_number || '@' || grid_row || ',' || grid_column, ' '
                                      ORDER BY grid_row, grid_column) AS layout
                    FROM venue_seats GROUP BY venue_id
                ) AS layouts""", Integer.class);
        assertThat(distinctLayouts).isGreaterThanOrEqualTo(2);

        Integer venuesWithGaps = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT venue_id FROM venue_seats GROUP BY venue_id
                    HAVING count(*) < max(grid_row) * max(grid_column)
                ) AS gapped""", Integer.class);
        assertThat(venuesWithGaps).isPositive();
    }
}
