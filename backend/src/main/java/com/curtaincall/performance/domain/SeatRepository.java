package com.curtaincall.performance.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** 공연의 좌석을 row, number 오름차순으로(spec 5). row는 COLLATE "C"라 코드 포인트 순서다(V2 마이그레이션). */
    @Query("select s from Seat s where s.performance.id = :performanceId order by s.row asc, s.number asc")
    List<Seat> findByPerformanceIdOrderByRowAndNumber(long performanceId);
}
