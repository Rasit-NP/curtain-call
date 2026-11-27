package com.curtaincall.hold.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.auth.domain.UserRepository;
import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.hold.domain.Hold;
import com.curtaincall.hold.domain.HoldLimit;
import com.curtaincall.hold.domain.HoldRepository;
import com.curtaincall.hold.domain.HoldStatus;
import com.curtaincall.hold.domain.SeatAvailability;
import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.PerformanceRepository;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatOrder;
import com.curtaincall.performance.domain.SeatRepository;

/**
 * 좌석 묶음 선점(spec 6.4). 한 트랜잭션에서 처리해 전부 잡거나 하나도 잡지 않는다.
 *
 * <p>동시성은 DB 행 잠금으로 보장한다(spec 2.4, 결정 0073). JVM 안의 락은 쓰지 않는다.
 * <ol>
 *   <li>사용자 행을 잠가 같은 사용자의 선점 요청을 한 줄로 세운다(R2). 앞 요청이 끝난 뒤 보유 좌석 수를 센다.</li>
 *   <li>404 검사와 한도 검사(R2)는 좌석을 잠그기 전에 한다. 그래서 잠그는 좌석은 4석 이하이고, 한도를 넘는 요청은 좌석을 잠그지 않는다.</li>
 *   <li>좌석은 id 오름차순으로 한 번에 잠근다(R1). 다른 요청이 잡고 있던 좌석은 그 요청이 끝난 뒤의 값으로 판정한다.</li>
 * </ol>
 *
 * <p>현재 시각은 요청마다 한 번 읽어 보유 좌석 수, 좌석 판정, 만료 시각에 같은 값을 쓴다.
 */
@Service
public class HoldService {

    private final Clock clock;
    private final UserRepository userRepository;
    private final PerformanceRepository performanceRepository;
    private final SeatRepository seatRepository;
    private final HoldRepository holdRepository;

    public HoldService(Clock clock, UserRepository userRepository, PerformanceRepository performanceRepository,
            SeatRepository seatRepository, HoldRepository holdRepository) {
        this.clock = clock;
        this.userRepository = userRepository;
        this.performanceRepository = performanceRepository;
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
    }

    /**
     * 오류 순서(spec 6.4, 400은 컨트롤러의 요청 검증): 404 NOT_FOUND(공연이 없거나 그 공연의 좌석이 아닌 것이 있음)
     * → 409 HOLD_LIMIT_EXCEEDED(보유 좌석 수 + 요청 좌석 수 &gt; 4) → 409 SEAT_ALREADY_HELD(유효한 선점이 있거나 판매된 좌석).
     *
     * @param seatIds 중복 없는 좌석 id(요청 검증을 지난 값). 순서는 상관없다.
     */
    @Transactional
    public HoldDetail create(long userId, long performanceId, List<Long> seatIds) {
        // 토큰은 서명·만료만 검증하므로 사용자 행이 없을 수도 있지만 따로 처리하지 않는다(결정 0071). 그때는 잠글 행이 없고,
        // 선점을 저장할 때 외래 키가 막아 500 INTERNAL_ERROR가 된다.
        userRepository.lockById(userId);

        Performance performance = performanceRepository.findWithVenueById(performanceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
        if (seatRepository.countByPerformanceIdAndIdIn(performanceId, seatIds.toArray(Long[]::new)) != seatIds.size()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "공연에 없는 좌석이 있습니다.");
        }

        Instant now = clock.instant();
        List<Hold> activeHolds = holdRepository.findByUserIdAndPerformanceIdAndStatus(userId, performanceId, HoldStatus.ACTIVE);
        if (!HoldLimit.allows(HoldLimit.heldSeatCount(activeHolds, now), seatIds.size())) {
            throw new ApiException(ErrorCode.HOLD_LIMIT_EXCEEDED);
        }

        List<Seat> seats = seatRepository.lockByPerformanceIdAndIdInOrderById(performanceId, seatIds);
        Map<Long, Hold> holders = holdRepository.findHoldersOf(seats);
        if (!seats.stream().allMatch(seat -> SeatAvailability.isHoldable(seat, holders, now))) {
            throw new ApiException(ErrorCode.SEAT_ALREADY_HELD);
        }

        Hold hold = holdRepository.save(Hold.create(userId, performanceId, seatIds, now));
        seats.forEach(seat -> seat.holdBy(hold.getId()));
        return new HoldDetail(hold, performance, seats.stream().sorted(SeatOrder.BY_ROW_AND_NUMBER).toList(), hold.isValidAt(now));
    }
}
