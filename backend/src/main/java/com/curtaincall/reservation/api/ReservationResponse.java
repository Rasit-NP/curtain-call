package com.curtaincall.reservation.api;

import java.time.Instant;
import java.util.List;

import com.curtaincall.performance.api.PerformanceSummaryResponse;
import com.curtaincall.performance.api.SeatRefResponse;
import com.curtaincall.reservation.application.ReservationDetail;

/** spec 5 {@code Reservation}. seats는 row, number 오름차순이고 amount는 결제 금액(공연 가격 × 좌석 수)이다. */
public record ReservationResponse(
        long id,
        PerformanceSummaryResponse performance,
        List<SeatRefResponse> seats,
        long amount,
        Instant reservedAt) {

    public static ReservationResponse from(ReservationDetail detail) {
        return new ReservationResponse(
                detail.reservation().getId(),
                PerformanceSummaryResponse.from(detail.performance()),
                detail.seats().stream().map(SeatRefResponse::from).toList(),
                detail.reservation().getAmount(),
                detail.reservation().getReservedAt());
    }
}
