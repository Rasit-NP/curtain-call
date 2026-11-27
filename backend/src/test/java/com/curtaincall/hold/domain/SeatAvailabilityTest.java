package com.curtaincall.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.curtaincall.performance.domain.SeatStatus;

/** 좌석을 선점할 수 있는지(R1)와 좌석의 API 상태(R3, spec 2.2). 좌석을 잡은 선점(holder)은 좌석의 holdId가 가리키는 선점이다. */
class SeatAvailabilityTest {

    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

    /** 4분 남은 유효한 선점. */
    private final Hold validHolder = Hold.create(7L, 30L, List.of(101L), NOW.minusSeconds(60));
    /** 바로 지금(NOW = 만료 시각) 만료된 선점. */
    private final Hold expiredHolder = Hold.create(7L, 30L, List.of(101L), NOW.minus(Hold.VALIDITY));

    @Test
    @DisplayName("R1: 유효한 선점이 없고 판매되지 않은 좌석만 선점할 수 있다고 판정한다")
    void onlySeatsWithoutValidHoldAndNotSoldAreHoldable() {
        assertThat(SeatAvailability.isHoldable(SeatStatus.AVAILABLE, null, NOW)).isTrue();
        assertThat(SeatAvailability.isHoldable(SeatStatus.HELD, expiredHolder, NOW)).isTrue();
        assertThat(SeatAvailability.isHoldable(SeatStatus.HELD, validHolder, NOW)).isFalse();
        assertThat(SeatAvailability.isHoldable(SeatStatus.SOLD, null, NOW)).isFalse();
    }

    @Test
    @DisplayName("R3: 좌석의 API 상태는 저장 상태가 HELD라도 좌석을 잡은 선점이 만료됐으면 AVAILABLE이다")
    void apiStatusReflectsHolderExpiry() {
        assertThat(SeatAvailability.apiStatus(SeatStatus.AVAILABLE, null, NOW)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(SeatAvailability.apiStatus(SeatStatus.HELD, validHolder, NOW)).isEqualTo(SeatStatus.HELD);
        assertThat(SeatAvailability.apiStatus(SeatStatus.HELD, expiredHolder, NOW)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(SeatAvailability.apiStatus(SeatStatus.SOLD, null, NOW)).isEqualTo(SeatStatus.SOLD);
    }
}
