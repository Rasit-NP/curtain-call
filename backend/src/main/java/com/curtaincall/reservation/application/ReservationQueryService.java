package com.curtaincall.reservation.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.PerformanceRepository;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatOrder;
import com.curtaincall.performance.domain.SeatRepository;
import com.curtaincall.reservation.domain.Reservation;
import com.curtaincall.reservation.domain.ReservationRepository;

/**
 * 내 예매(spec 6.8). 잠그지 않고 읽는다. 예매는 만든 뒤 바뀌지 않는다(취소 없음, spec 8).
 *
 * <p>예매(좌석 목록 포함), 공연(공연장 포함), 좌석을 각각 한 번에 읽어 예매 수만큼 조회가 늘지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final PerformanceRepository performanceRepository;
    private final SeatRepository seatRepository;

    public ReservationQueryService(ReservationRepository reservationRepository, PerformanceRepository performanceRepository,
            SeatRepository seatRepository) {
        this.reservationRepository = reservationRepository;
        this.performanceRepository = performanceRepository;
        this.seatRepository = seatRepository;
    }

    /** 사용자의 예매를 reservedAt 내림차순(같으면 id 내림차순)으로. 좌석은 row, number 오름차순이다(spec 5). 없으면 빈 목록. */
    public List<ReservationDetail> findMine(long userId) {
        List<Reservation> reservations = reservationRepository.findWithSeatIdsByUserIdLatestFirst(userId);
        if (reservations.isEmpty()) {
            return List.of();
        }
        Set<Long> performanceIds = reservations.stream().map(Reservation::getPerformanceId).collect(Collectors.toSet());
        Map<Long, Performance> performances = performanceRepository.findAllWithVenueByIdIn(performanceIds).stream()
                .collect(Collectors.toMap(Performance::getId, Function.identity()));
        Set<Long> seatIds = reservations.stream().flatMap(r -> r.getSeatIds().stream()).collect(Collectors.toSet());
        Map<Long, Seat> seats = seatRepository.findAllById(seatIds).stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));
        return reservations.stream()
                .map(reservation -> new ReservationDetail(
                        reservation,
                        performances.get(reservation.getPerformanceId()),
                        reservation.getSeatIds().stream().map(seats::get).sorted(SeatOrder.BY_ROW_AND_NUMBER).toList()))
                .toList();
    }
}
