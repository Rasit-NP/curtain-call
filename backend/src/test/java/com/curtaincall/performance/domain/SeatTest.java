package com.curtaincall.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 좌석의 상태 전이(spec 2.3). 저장 상태가 HELD일 때만 holdId가 있다(spec 2.1). 스프링 컨텍스트 없이 엔티티만 만든다. */
class SeatTest {

    @Test
    @DisplayName("선점이 잡은 좌석을 해제하면 AVAILABLE이 되고 holdId를 비운다")
    void releaseMakesSeatAvailable() {
        Seat seat = new Seat();
        seat.holdBy(31L);

        seat.release();

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHoldId()).isNull();
    }

    @Test
    @DisplayName("HELD가 아닌 좌석은 해제할 수 없다")
    void onlyHeldSeatCanBeReleased() {
        Seat seat = new Seat();
        seat.holdBy(31L);
        seat.release();

        assertThatThrownBy(seat::release).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제가 승인되면 선점이 잡은 좌석은 SOLD가 되고 holdId를 비운다")
    void sellMakesSeatSold() {
        Seat seat = new Seat();
        seat.holdBy(31L);

        seat.sell(31L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(seat.getHoldId()).isNull();
    }

    @Test
    @DisplayName("결제하는 선점이 잡지 않은 좌석(다른 선점이 잡은 좌석, AVAILABLE, 이미 SOLD)은 팔 수 없다")
    void onlySeatHeldByThePayingHoldCanBeSold() {
        Seat heldByOther = new Seat();
        heldByOther.holdBy(32L);
        assertThatThrownBy(() -> heldByOther.sell(31L)).isInstanceOf(IllegalStateException.class);
        assertThat(heldByOther.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(heldByOther.getHoldId()).isEqualTo(32L);

        Seat available = new Seat();
        available.holdBy(31L);
        available.release();
        assertThatThrownBy(() -> available.sell(31L)).isInstanceOf(IllegalStateException.class);

        Seat sold = new Seat();
        sold.holdBy(31L);
        sold.sell(31L);
        assertThatThrownBy(() -> sold.sell(31L)).isInstanceOf(IllegalStateException.class);
    }
}
