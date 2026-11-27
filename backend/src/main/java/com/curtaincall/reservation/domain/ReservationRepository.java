package com.curtaincall.reservation.domain;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 보유 좌석 수(R2, spec 2.2) 중 예매 몫: 같은 사용자·같은 공연의 예매 좌석 수. 결제된 선점의 좌석은 여기서만 센다
     * (선점 몫은 ACTIVE 선점만 읽으므로 PAID 선점을 세지 않는다).
     */
    @Query(value = """
            SELECT count(*) FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id
            WHERE r.user_id = :userId AND r.performance_id = :performanceId""", nativeQuery = true)
    long countSeatsByUserIdAndPerformanceId(long userId, long performanceId);

    /** 내 예매(spec 6.8): reservedAt 내림차순, 같으면 id 내림차순. 좌석 목록을 함께 읽는다. */
    @EntityGraph(attributePaths = "seatIds")
    @Query("select r from Reservation r where r.userId = :userId order by r.reservedAt desc, r.id desc")
    List<Reservation> findWithSeatIdsByUserIdLatestFirst(long userId);
}
