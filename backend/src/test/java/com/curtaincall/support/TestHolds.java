package com.curtaincall.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트가 선점 관련 DB 상태를 SQL로 직접 보고 만든다. API 테스트는 선점을 {@code POST /api/holds}로 만들고({@link HoldRequests}),
 * 결과를 응답과 함께 DB 최종 상태로 확인한다. 스키마 제약 테스트만 선점 행과 좌석 상태를 SQL로 직접 넣는다.
 */
public class TestHolds {

    private final JdbcTemplate jdbcTemplate;

    public TestHolds(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 선점 행을 SQL로 직접 만들고 id를 돌려준다(스키마 제약 테스트용). 좌석 목록과 좌석 상태는 바꾸지 않는다. */
    public long insertHold(long userId, long performanceId, String status, Instant createdAt, Instant expiresAt) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO holds (user_id, performance_id, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id""",
                Long.class, userId, performanceId, status, Timestamp.from(createdAt), Timestamp.from(expiresAt));
        if (id == null) {
            throw new IllegalStateException("선점 id를 돌려받지 못했다");
        }
        return id;
    }

    /** 좌석의 저장 상태와 holdId를 SQL로 직접 바꾼다(스키마 제약 테스트용). */
    public void updateSeat(long seatId, String status, @Nullable Long holdId) {
        jdbcTemplate.update("UPDATE seats SET status = ?, hold_id = ? WHERE id = ?", status, holdId, seatId);
    }

    /** 좌석의 저장 상태(API 상태가 아님). */
    public String seatStatus(long seatId) {
        return jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId);
    }

    /** 좌석을 지금 잡은 선점. 없으면 null. */
    public @Nullable Long seatHoldId(long seatId) {
        return jdbcTemplate.queryForObject("SELECT hold_id FROM seats WHERE id = ?", Long.class, seatId);
    }

    /** 공연에서 저장 상태가 HELD인 좌석의 id(오름차순). */
    public List<Long> heldSeatIds(long performanceId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM seats WHERE performance_id = ? AND status = 'HELD' ORDER BY id", Long.class, performanceId);
    }

    /** 모든 선점 행의 수. */
    public int countHolds() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM holds", Integer.class);
        return count == null ? 0 : count;
    }

    /** 공연의 선점 행의 수. */
    public int countHolds(long performanceId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM holds WHERE performance_id = ?", Integer.class,
                performanceId);
        return count == null ? 0 : count;
    }

    /** 선점의 좌석 목록(만든 때의 묶음, 좌석 id 오름차순). 좌석을 지금 잡고 있는지와는 다르다({@link #seatHoldId}). */
    public List<Long> seatIdsOf(long holdId) {
        return jdbcTemplate.queryForList("SELECT seat_id FROM hold_seats WHERE hold_id = ? ORDER BY seat_id", Long.class,
                holdId);
    }

    /** 선점의 저장 상태(ACTIVE·RELEASED·PAID). 만료는 저장하지 않으므로 만료된 선점도 ACTIVE다. */
    public String holdStatus(long holdId) {
        String status = jdbcTemplate.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, holdId);
        if (status == null) {
            throw new IllegalStateException("선점이 없다: " + holdId);
        }
        return status;
    }

    /** 선점을 만든 사용자. */
    public long userIdOf(long holdId) {
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM holds WHERE id = ?", Long.class, holdId);
        if (userId == null) {
            throw new IllegalStateException("선점이 없다: " + holdId);
        }
        return userId;
    }
}
