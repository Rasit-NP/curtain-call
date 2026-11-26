package com.curtaincall.performance.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** 공연의 좌석을 row, number 오름차순으로(spec 5). row는 COLLATE "C"라 코드 포인트 순서다(V2 마이그레이션). */
    @Query("select s from Seat s where s.performance.id = :performanceId order by s.row asc, s.number asc")
    List<Seat> findByPerformanceIdOrderByRowAndNumber(long performanceId);

    /**
     * ids 중 이 공연의 좌석 수(선점의 404 검사, spec 6.4). 좌석 엔티티를 읽지 않으므로, 뒤의 잠금 조회가 영속성 컨텍스트에 남은 값이 아닌
     * 잠근 뒤의 값을 읽는다.
     *
     * <p>ids는 배열 매개변수 하나로 넘긴다({@code = ANY(?)}). {@code IN (:ids)}는 id마다 매개변수를 하나씩 만들어, 요청의 좌석 id가
     * PostgreSQL JDBC 드라이버의 상한(65,535개)을 넘으면 500이 된다. 이 검사는 좌석 수의 상한(4석 한도) 검사보다 앞이라 ids의 길이에
     * 상한이 없다.
     */
    @Query(value = "SELECT count(*) FROM seats WHERE performance_id = :performanceId AND id = ANY(:ids)", nativeQuery = true)
    long countByPerformanceIdAndIdIn(long performanceId, Long[] ids);

    /**
     * 이 공연의 좌석 중 ids를 id 오름차순으로 잠그고 읽는다(R1, spec 2.4). 요청에 담긴 순서와 상관없이 모든 요청이 같은 순서로 잠가,
     * 같은 좌석들을 다른 순서로 담은 묶음끼리 교착 상태가 생기지 않는다. PostgreSQL에서는 {@code SELECT ... ORDER BY id FOR NO KEY UPDATE}이고,
     * 정렬한 순서대로 잠근다. 다른 트랜잭션이 잡고 있으면 끝날 때까지 기다렸다가 그 트랜잭션이 바꾼 값을 읽는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.performance.id = :performanceId and s.id in :ids order by s.id asc")
    List<Seat> lockByPerformanceIdAndIdInOrderById(long performanceId, Collection<Long> ids);
}
