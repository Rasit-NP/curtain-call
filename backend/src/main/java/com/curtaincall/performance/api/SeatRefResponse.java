package com.curtaincall.performance.api;

import com.curtaincall.performance.domain.Seat;

/** spec 5 {@code SeatRef}. 선점·예매 응답의 좌석이다. {@code row}·{@code number}는 표시 이름({@code A-3})이다. */
public record SeatRefResponse(long id, String row, int number) {

    public static SeatRefResponse from(Seat seat) {
        return new SeatRefResponse(seat.getId(), seat.getRow(), seat.getNumber());
    }
}
