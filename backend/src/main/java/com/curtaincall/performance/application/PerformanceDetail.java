package com.curtaincall.performance.application;

import java.util.List;

import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatStatus;

/**
 * 공연 상세 조회 결과. 공연(공연장 포함)과 그 공연의 좌석(row, number 오름차순).
 *
 * @param seats 좌석과 조회 시각의 API 상태(spec 2.2)
 */
public record PerformanceDetail(Performance performance, List<SeatState> seats) {

    /** 좌석과 그 API 상태. 저장 상태가 HELD라도 좌석을 잡은 선점이 만료됐으면 status는 AVAILABLE이다. */
    public record SeatState(Seat seat, SeatStatus status) {
    }
}
