package com.curtaincall.hold.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.curtaincall.performance.domain.SeatStatus;

/** 좌석의 API 상태와 선점 가능 여부(spec 2.2). 구현 전 뼈대. */
public final class SeatAvailability {

    private SeatAvailability() {
    }

    public static SeatStatus apiStatus(SeatStatus stored, @Nullable Hold holder, Instant now) {
        throw new UnsupportedOperationException("구현 전");
    }

    public static boolean isHoldable(SeatStatus stored, @Nullable Hold holder, Instant now) {
        throw new UnsupportedOperationException("구현 전");
    }
}
