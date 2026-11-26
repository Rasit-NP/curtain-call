package com.curtaincall.support;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트가 공연장·배치·공연·좌석을 직접 만든다(시드에 기대지 않는다).
 *
 * <p>JPA 엔티티를 거치지 않고 SQL로 넣는다. 그래서 API 테스트가 엔티티 매핑까지 함께 검증하고, 스키마 제약 테스트는
 * 제약 자체를 검증한다.
 */
public class TestPerformances {

    private final JdbcTemplate jdbcTemplate;

    public TestPerformances(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 공연장을 만들고 id를 돌려준다. */
    public long createVenue(String name) {
        return insert("INSERT INTO venues (name) VALUES (?) RETURNING id", name);
    }

    /** 공연장 배치에 좌석 하나를 더한다. gridRow·gridColumn은 배치 격자의 줄·칸(1부터)이다. */
    public long addVenueSeat(long venueId, String row, int number, int gridRow, int gridColumn) {
        return insert("""
                INSERT INTO venue_seats (venue_id, seat_row, seat_number, grid_row, grid_column)
                VALUES (?, ?, ?, ?, ?) RETURNING id""", venueId, row, number, gridRow, gridColumn);
    }

    /** 공연을 만들고 id를 돌려준다. 좌석은 {@link #addSeat}로 따로 더한다. */
    public long createPerformance(String title, long venueId, Instant startsAt, int price) {
        return insert("""
                INSERT INTO performances (title, venue_id, starts_at, price)
                VALUES (?, ?, ?, ?) RETURNING id""", title, venueId, Timestamp.from(startsAt), price);
    }

    /** 공연 좌석 하나를 AVAILABLE로 더한다. */
    public long addSeat(long performanceId, String row, int number, int gridRow, int gridColumn) {
        return addSeat(performanceId, row, number, gridRow, gridColumn, "AVAILABLE");
    }

    /** 공연 좌석 하나를 정한 저장 상태로 더한다. */
    public long addSeat(long performanceId, String row, int number, int gridRow, int gridColumn, String status) {
        return insert("""
                INSERT INTO seats (performance_id, seat_row, seat_number, grid_row, grid_column, status)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", performanceId, row, number, gridRow, gridColumn, status);
    }

    /** 공연 제목을 바꾼다. 행의 물리적 저장 순서를 id 순서와 다르게 만들 때 쓴다. */
    public void renamePerformance(long performanceId, String title) {
        jdbcTemplate.update("UPDATE performances SET title = ? WHERE id = ?", title, performanceId);
    }

    private long insert(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (id == null) {
            throw new IllegalStateException("id를 돌려받지 못했다: " + sql);
        }
        return id;
    }
}
