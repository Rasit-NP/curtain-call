package com.curtaincall.performance.application;

import java.util.List;

import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.Seat;

/** 공연 상세 조회 결과. 공연(공연장 포함)과 그 공연의 좌석(row, number 오름차순). */
public record PerformanceDetail(Performance performance, List<Seat> seats) {
}
