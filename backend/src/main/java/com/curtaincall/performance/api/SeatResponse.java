package com.curtaincall.performance.api;

import com.curtaincall.performance.application.PerformanceDetail.SeatState;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatStatus;

/**
 * spec 5 {@code Seat}. {@code row}·{@code number}는 표시 이름, {@code gridRow}·{@code gridColumn}은 배치 격자의 줄·칸(1부터)이다.
 * 프론트엔드는 위치를 CSS Grid의 grid-row·grid-column에 넣어 그린다(결정 0047). {@code status}는 API 상태다(spec 2.2, 만료 반영).
 * 누가 선점했는지는 알려 주지 않는다(spec 6.3).
 */
public record SeatResponse(long id, String row, int number, int gridRow, int gridColumn, SeatStatus status) {

    public static SeatResponse from(SeatState state) {
        Seat seat = state.seat();
        return new SeatResponse(
                seat.getId(), seat.getRow(), seat.getNumber(), seat.getGridRow(), seat.getGridColumn(), state.status());
    }
}
