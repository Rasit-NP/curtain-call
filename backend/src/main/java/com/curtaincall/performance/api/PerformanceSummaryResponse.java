package com.curtaincall.performance.api;

import java.time.Instant;

import com.curtaincall.performance.domain.Performance;

/** spec 5 {@code PerformanceSummary}. {@code venue}는 공연장 이름이다. */
public record PerformanceSummaryResponse(long id, String title, String venue, Instant startsAt, int price) {

    public static PerformanceSummaryResponse from(Performance performance) {
        return new PerformanceSummaryResponse(
                performance.getId(),
                performance.getTitle(),
                performance.getVenue().getName(),
                performance.getStartsAt(),
                performance.getPrice());
    }
}
