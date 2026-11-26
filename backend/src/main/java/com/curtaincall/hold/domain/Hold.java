package com.curtaincall.hold.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/** 선점(spec 2.1). 구현 전 뼈대. */
public class Hold {

    public static final Duration VALIDITY = Duration.ofMinutes(5);

    public static Hold create(long userId, long performanceId, Collection<Long> seatIds, Instant now) {
        throw new UnsupportedOperationException("구현 전");
    }

    Hold(long userId, long performanceId, Collection<Long> seatIds, HoldStatus status, Instant createdAt) {
        throw new UnsupportedOperationException("구현 전");
    }

    public boolean isValidAt(Instant now) {
        throw new UnsupportedOperationException("구현 전");
    }

    public Long getId() {
        throw new UnsupportedOperationException("구현 전");
    }

    public long getUserId() {
        throw new UnsupportedOperationException("구현 전");
    }

    public long getPerformanceId() {
        throw new UnsupportedOperationException("구현 전");
    }

    public HoldStatus getStatus() {
        throw new UnsupportedOperationException("구현 전");
    }

    public Instant getCreatedAt() {
        throw new UnsupportedOperationException("구현 전");
    }

    public Instant getExpiresAt() {
        throw new UnsupportedOperationException("구현 전");
    }

    public Set<Long> getSeatIds() {
        throw new UnsupportedOperationException("구현 전");
    }
}
