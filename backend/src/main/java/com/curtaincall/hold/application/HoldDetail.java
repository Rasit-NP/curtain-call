package com.curtaincall.hold.application;

import java.util.List;

import com.curtaincall.hold.domain.Hold;
import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.Seat;

/**
 * 선점 응답(spec 5 {@code Hold})에 필요한 값. 공연(공연장 포함)과 좌석(row, number 오름차순)은 응답에 필요한 값을 모두 읽어 둔 상태다.
 *
 * @param valid 응답을 만든 시각에 유효한 선점인지(ACTIVE), 만료됐는지(EXPIRED)
 */
public record HoldDetail(Hold hold, Performance performance, List<Seat> seats, boolean valid) {

    /** spec 2.2 '결제 금액': 공연 가격 × 선점 좌석 수(결제 금액과 같은 규칙, {@link Hold#amount}). */
    public long amount() {
        return hold.amount(performance.getPrice());
    }
}
