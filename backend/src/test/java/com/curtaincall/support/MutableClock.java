package com.curtaincall.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 통합 테스트용 시계. 시각을 정하거나 옮길 수 있고, 테스트마다 {@link #DEFAULT_INSTANT}로 되돌린다.
 */
public class MutableClock extends Clock {

    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-12-01T10:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(DEFAULT_INSTANT);

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("테스트 시계는 UTC로만 쓴다");
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    public void setInstant(Instant instant) {
        now.set(instant);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    public void reset() {
        now.set(DEFAULT_INSTANT);
    }
}
