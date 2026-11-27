package com.curtaincall.reservation.application;

import java.util.List;

import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.reservation.domain.Reservation;

/**
 * 예매 응답(spec 5 {@code Reservation})에 필요한 값. 공연(공연장 포함)과 좌석(row, number 오름차순)은 응답에 필요한 값을 모두 읽어 둔 상태다.
 * 결제 응답(6.7)과 내 예매(6.8)가 함께 쓴다.
 */
public record ReservationDetail(Reservation reservation, Performance performance, List<Seat> seats) {
}
