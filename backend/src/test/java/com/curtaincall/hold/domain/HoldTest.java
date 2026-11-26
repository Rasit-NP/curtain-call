package com.curtaincall.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 선점의 만료(R3, spec 2.1, 2.2)와 묶음 크기(spec 2.1). 스프링 컨텍스트 없이 시각을 값으로 넘긴다. */
class HoldTest {

    private static final Instant CREATED_AT = Instant.parse("2026-12-01T10:00:00Z");

    @Test
    @DisplayName("R3: 선점의 만료 시각은 만든 시각 + 5분이다")
    void expiresFiveMinutesAfterCreation() {
        Hold hold = Hold.create(7L, 30L, List.of(101L, 104L), CREATED_AT);

        assertThat(hold.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(hold.getExpiresAt()).isEqualTo(Instant.parse("2026-12-01T10:05:00Z"));
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.getUserId()).isEqualTo(7L);
        assertThat(hold.getPerformanceId()).isEqualTo(30L);
        assertThat(hold.getSeatIds()).containsExactlyInAnyOrder(101L, 104L);
    }

    @Test
    @DisplayName("R3: 현재 시각이 만료 시각 1초 전이면 유효하고, 같거나 이후면 만료로 판정한다")
    void validOnlyBeforeExpiresAt() {
        Hold hold = Hold.create(7L, 30L, List.of(101L), CREATED_AT);
        Instant expiresAt = hold.getExpiresAt();

        assertThat(hold.isValidAt(CREATED_AT)).isTrue();
        assertThat(hold.isValidAt(expiresAt.minusSeconds(1))).isTrue();
        assertThat(hold.isValidAt(expiresAt.minusNanos(1))).isTrue();
        assertThat(hold.isValidAt(expiresAt)).isFalse();
        assertThat(hold.isValidAt(expiresAt.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("해제·결제된 선점은 만료 시각 전이라도 유효하지 않다(유효한 선점은 ACTIVE이고 만료 전)")
    void releasedOrPaidHoldIsNotValid() {
        Instant beforeExpiry = CREATED_AT.plusSeconds(60);

        assertThat(new Hold(7L, 30L, List.of(101L), HoldStatus.RELEASED, CREATED_AT).isValidAt(beforeExpiry)).isFalse();
        assertThat(new Hold(7L, 30L, List.of(101L), HoldStatus.PAID, CREATED_AT).isValidAt(beforeExpiry)).isFalse();
    }

    @Test
    @DisplayName("선점은 서로 다른 좌석 1~4석의 묶음으로만 만든다(0석, 5석, 같은 좌석 두 번은 거부)")
    void holdIsBundleOfOneToFourDistinctSeats() {
        assertThatThrownBy(() -> Hold.create(7L, 30L, List.of(), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hold.create(7L, 30L, List.of(1L, 2L, 3L, 4L, 5L), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hold.create(7L, 30L, List.of(1L, 1L), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> Hold.create(7L, 30L, List.of(1L), CREATED_AT)).doesNotThrowAnyException();
        assertThatCode(() -> Hold.create(7L, 30L, List.of(1L, 2L, 3L, 4L), CREATED_AT)).doesNotThrowAnyException();
    }
}
