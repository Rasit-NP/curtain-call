package com.curtaincall.support;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트가 예매·결제의 DB 상태를 SQL로 직접 본다. 결제·예매는 API({@link PaymentRequests})로 만들고, 결과를 응답과 함께 DB 최종 상태로 확인한다.
 */
public class TestPayments {

    private final JdbcTemplate jdbcTemplate;

    public TestPayments(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 모든 예매 행의 수. */
    public int countReservations() {
        return count("SELECT count(*) FROM reservations");
    }

    /** 선점으로 만든 예매의 수. */
    public int countReservationsOf(long holdId) {
        return count("SELECT count(*) FROM reservations WHERE hold_id = ?", holdId);
    }

    /** 예매의 좌석(좌석 id 오름차순). */
    public List<Long> reservationSeatIds(long reservationId) {
        return jdbcTemplate.queryForList(
                "SELECT seat_id FROM reservation_seats WHERE reservation_id = ? ORDER BY seat_id", Long.class, reservationId);
    }

    /** 예매 행 하나(user_id, performance_id, hold_id, amount, reserved_at). */
    public Map<String, Object> reservation(long reservationId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, performance_id, hold_id, amount, reserved_at FROM reservations WHERE id = ?", reservationId);
    }

    /** 모든 결제 행의 수. */
    public int countPayments() {
        return count("SELECT count(*) FROM payments");
    }

    /** 선점의 결제 상태(SUCCEEDED·FAILED)를 결제 id 순서로. */
    public List<String> paymentStatusesOf(long holdId) {
        return jdbcTemplate.queryForList("SELECT status FROM payments WHERE hold_id = ? ORDER BY id", String.class, holdId);
    }

    /** 선점의 결제 id(오름차순). */
    public List<Long> paymentIdsOf(long holdId) {
        return jdbcTemplate.queryForList("SELECT id FROM payments WHERE hold_id = ? ORDER BY id", Long.class, holdId);
    }

    /** 결제 행 하나(user_id, hold_id, amount, status, reservation_id). */
    public Map<String, Object> payment(long paymentId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, hold_id, amount, status, reservation_id FROM payments WHERE id = ?", paymentId);
    }

    /** 선점의 만료 시각(저장 값). */
    public @Nullable Timestamp holdExpiresAt(long holdId) {
        return jdbcTemplate.queryForObject("SELECT expires_at FROM holds WHERE id = ?", Timestamp.class, holdId);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
