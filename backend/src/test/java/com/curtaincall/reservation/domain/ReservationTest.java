package com.curtaincall.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.curtaincall.hold.domain.Hold;

/** 예매는 결제된 선점으로만 만들고, 선점의 좌석 묶음을 그대로 옮긴다(spec 2.1, 2.3). 스프링 컨텍스트 없이 만든다. */
class ReservationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-12-01T10:00:00Z");

    @Test
    @DisplayName("결제된 선점으로 만든 예매는 선점의 사용자·공연·좌석 묶음과 금액·예매 시각을 가진다")
    void reservationCopiesPaidHold() throws Exception {
        Hold hold = savedHold(31L, List.of(101L, 104L));
        Instant judgedAt = CREATED_AT.plusSeconds(180);
        hold.pay(judgedAt);

        Reservation reservation = Reservation.of(hold, 60000L, judgedAt);

        assertThat(reservation.getUserId()).isEqualTo(7L);
        assertThat(reservation.getPerformanceId()).isEqualTo(30L);
        assertThat(reservation.getHoldId()).isEqualTo(31L);
        assertThat(reservation.getSeatIds()).containsExactlyInAnyOrder(101L, 104L);
        assertThat(reservation.getAmount()).isEqualTo(60000L);
        assertThat(reservation.getReservedAt()).isEqualTo(judgedAt);
    }

    @Test
    @DisplayName("결제되지 않은 선점(ACTIVE)으로는 예매를 만들 수 없다")
    void onlyPaidHoldMakesReservation() throws Exception {
        Hold active = savedHold(31L, List.of(101L));

        assertThatThrownBy(() -> Reservation.of(active, 30000L, CREATED_AT)).isInstanceOf(IllegalStateException.class);
    }

    /** 저장된 선점처럼 id를 넣는다(예매는 선점 id를 가진다). */
    private static Hold savedHold(long id, List<Long> seatIds) throws Exception {
        Hold hold = Hold.create(7L, 30L, seatIds, CREATED_AT);
        Field idField = Hold.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(hold, id);
        return hold;
    }
}
