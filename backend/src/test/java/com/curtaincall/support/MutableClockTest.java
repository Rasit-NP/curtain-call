package com.curtaincall.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    private final MutableClock clock = new MutableClock();

    @Test
    @DisplayName("처음에는 정해진 시각(UTC)을 가리킨다")
    void startsAtDefaultInstant() {
        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("시각을 정하거나 옮길 수 있다")
    void setAndAdvance() {
        Instant at = Instant.parse("2027-03-01T00:00:00Z");

        clock.setInstant(at);
        assertThat(clock.instant()).isEqualTo(at);

        clock.advance(Duration.ofMinutes(5));
        assertThat(clock.instant()).isEqualTo(at.plusSeconds(300));
        assertThat(clock.millis()).isEqualTo(at.plusSeconds(300).toEpochMilli());
    }

    @Test
    @DisplayName("되돌리면 정해진 시각으로 돌아간다")
    void resetReturnsToDefault() {
        clock.advance(Duration.ofHours(1));

        clock.reset();

        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }
}
