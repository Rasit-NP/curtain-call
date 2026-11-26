package com.curtaincall.performance.domain;

import java.util.Comparator;

/** 좌석 목록의 순서(spec 5). 구현 전 뼈대. */
public final class SeatOrder {

    public static final Comparator<Seat> BY_ROW_AND_NUMBER = (a, b) -> {
        throw new UnsupportedOperationException("구현 전");
    };

    private SeatOrder() {
    }

    static int compareRows(String a, String b) {
        throw new UnsupportedOperationException("구현 전");
    }
}
