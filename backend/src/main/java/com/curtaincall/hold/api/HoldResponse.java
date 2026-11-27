package com.curtaincall.hold.api;

import java.time.Instant;
import java.util.List;

import com.curtaincall.hold.application.HoldDetail;
import com.curtaincall.performance.api.PerformanceSummaryResponse;
import com.curtaincall.performance.api.SeatRefResponse;

/**
 * spec 5 {@code Hold}. seats는 row, number 오름차순이고 amount는 공연 가격 × 좌석 수다.
 *
 * @param status 유효한 선점이면 ACTIVE, 만료된 선점이면 EXPIRED. 해제·결제된 선점은 응답으로 나오지 않는다.
 */
public record HoldResponse(
        long id,
        Status status,
        PerformanceSummaryResponse performance,
        List<SeatRefResponse> seats,
        long amount,
        Instant expiresAt) {

    /** 선점의 API 상태(spec 5). 저장 상태(ACTIVE·RELEASED·PAID)와 다르다. */
    public enum Status {
        ACTIVE,
        EXPIRED
    }

    public static HoldResponse from(HoldDetail detail) {
        return new HoldResponse(
                detail.hold().getId(),
                detail.valid() ? Status.ACTIVE : Status.EXPIRED,
                PerformanceSummaryResponse.from(detail.performance()),
                detail.seats().stream().map(SeatRefResponse::from).toList(),
                detail.amount(),
                detail.hold().getExpiresAt());
    }
}
