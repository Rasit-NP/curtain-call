package com.curtaincall.performance.domain;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 좌석 목록의 순서(spec 5): row, number 오름차순. row는 문자열 코드 포인트 순서, number는 숫자 순서다(A-2가 A-10보다 앞).
 *
 * <p>공연 상세는 DB가 정렬한다(seat_row COLLATE "C": UTF-8 바이트 순서 = 코드 포인트 순서, V2 마이그레이션). 이미 읽은 좌석을
 * 자바에서 정렬할 때(선점 응답) 같은 순서가 되도록 이것을 쓴다. {@link String#compareTo}는 UTF-16 단위로 비교해 보충 문자에서
 * 코드 포인트 순서와 달라진다.
 */
public final class SeatOrder {

    public static final Comparator<Seat> BY_ROW_AND_NUMBER =
            Comparator.comparing(Seat::getRow, SeatOrder::compareRows).thenComparingInt(Seat::getNumber);

    private SeatOrder() {
    }

    static int compareRows(String a, String b) {
        return Arrays.compare(a.codePoints().toArray(), b.codePoints().toArray());
    }
}
