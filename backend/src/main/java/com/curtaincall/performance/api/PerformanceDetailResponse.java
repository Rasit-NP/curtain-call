package com.curtaincall.performance.api;

import java.time.Instant;
import java.util.List;

import com.curtaincall.performance.application.PerformanceDetail;
import com.curtaincall.performance.domain.Performance;

/** spec 6.3 응답: {@code PerformanceSummary}에 {@code seats}(row, number 오름차순)를 더한다. */
public record PerformanceDetailResponse(
        long id, String title, String venue, Instant startsAt, int price, List<SeatResponse> seats) {

    public static PerformanceDetailResponse from(PerformanceDetail detail) {
        Performance performance = detail.performance();
        return new PerformanceDetailResponse(
                performance.getId(),
                performance.getTitle(),
                performance.getVenue().getName(),
                performance.getStartsAt(),
                performance.getPrice(),
                detail.seats().stream().map(SeatResponse::from).toList());
    }
}
