package com.curtaincall.performance.api;

import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatStatus;

/**
 * spec 5 {@code Seat}. {@code row}·{@code number}는 표시 이름, {@code gridRow}·{@code gridColumn}은 배치 격자의 줄·칸(1부터)이다.
 * 프론트엔드는 위치를 CSS Grid의 grid-row·grid-column에 넣어 그린다(결정 0047).
 */
public record SeatResponse(long id, String row, int number, int gridRow, int gridColumn, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(), seat.getRow(), seat.getNumber(), seat.getGridRow(), seat.getGridColumn(), seat.getStatus());
    }
}
