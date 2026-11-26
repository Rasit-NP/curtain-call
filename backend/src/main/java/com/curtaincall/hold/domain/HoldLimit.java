package com.curtaincall.hold.domain;

import java.time.Instant;
import java.util.Collection;

/** R2 한도. 구현 전 뼈대. */
public final class HoldLimit {

    public static final int MAX_SEATS_PER_PERFORMANCE = 4;

    private HoldLimit() {
    }

    public static int heldSeatCount(Collection<Hold> holds, Instant now) {
        throw new UnsupportedOperationException("구현 전");
    }

    public static boolean allows(int heldSeats, int requestedSeats) {
        throw new UnsupportedOperationException("구현 전");
    }
}
