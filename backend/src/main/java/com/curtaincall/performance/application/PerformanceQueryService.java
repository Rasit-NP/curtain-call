package com.curtaincall.performance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.hold.domain.Hold;
import com.curtaincall.hold.domain.HoldRepository;
import com.curtaincall.hold.domain.SeatAvailability;
import com.curtaincall.performance.application.PerformanceDetail.SeatState;
import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.PerformanceRepository;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatRepository;

/**
 * 공연 조회(spec 6.2, 6.3). 돌려주는 엔티티는 응답에 필요한 값(공연장 포함)을 모두 읽어 둔 상태다(open-in-view를 끔).
 *
 * <p>좌석 status는 API 상태다(spec 2.2, R3 지연 만료): 저장 상태가 HELD라도 좌석을 잡은 선점(holdId)이 조회 시각에 만료됐으면
 * AVAILABLE이다. 만료를 저장하지 않으므로 조회할 때마다 판정한다.
 */
@Service
@Transactional(readOnly = true)
public class PerformanceQueryService {

    private final Clock clock;
    private final PerformanceRepository performanceRepository;
    private final SeatRepository seatRepository;
    private final HoldRepository holdRepository;

    public PerformanceQueryService(Clock clock, PerformanceRepository performanceRepository, SeatRepository seatRepository,
            HoldRepository holdRepository) {
        this.clock = clock;
        this.performanceRepository = performanceRepository;
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
    }

    /** 시작 시각 오름차순, 같으면 id 오름차순. 없으면 빈 목록. */
    public List<Performance> findAll() {
        return performanceRepository.findAllWithVenueOrderByStartsAt();
    }

    /** 공연이 없으면 404 NOT_FOUND. */
    public PerformanceDetail getDetail(long performanceId) {
        Performance performance = performanceRepository.findWithVenueById(performanceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
        List<Seat> seats = seatRepository.findByPerformanceIdOrderByRowAndNumber(performanceId);
        Map<Long, Hold> holders = holdRepository.findHoldersOf(seats);
        Instant now = clock.instant();
        return new PerformanceDetail(performance, seats.stream()
                .map(seat -> new SeatState(seat, SeatAvailability.apiStatus(seat, holders, now)))
                .toList());
    }
}
