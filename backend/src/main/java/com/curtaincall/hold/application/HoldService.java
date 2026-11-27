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
 * 좌석 묶음 선점(spec 6.4), 내 선점 조회(6.5)·해제(6.6). 선점은 한 트랜잭션에서 처리해 전부 잡거나 하나도 잡지 않는다.
 *
 * <p>동시성은 DB 행 잠금으로 보장한다(spec 2.4, 결정 0100). JVM 안의 락은 쓰지 않는다. 잠금 순서는 사용자 행 → 선점 행 → 좌석(id 오름차순)
 * 하나이고, 요청마다 필요한 잠금만 이 순서로 건다. 선점 생성은 아래와 같고, 해제는 {@link #release}에 적었다.
 * <ol>
 *   <li>사용자 행을 잠가 같은 사용자의 선점 요청을 한 줄로 세운다(R2). 앞 요청이 끝난 뒤 보유 좌석 수를 센다.</li>
 *   <li>404 검사와 한도 검사(R2)는 좌석을 잠그기 전에 한다. 그래서 잠그는 좌석은 4석 이하이고, 한도를 넘는 요청은 좌석을 잠그지 않는다.</li>
 *   <li>좌석은 id 오름차순으로 한 번에 잠근다(R1). 다른 요청이 잡고 있던 좌석은 그 요청이 끝난 뒤의 값으로 판정한다.</li>
 * </ol>
 *
 * <p>선점 생성은 현재 시각을 한 번 읽어 보유 좌석 수, 좌석 판정, 만료 시각에 같은 값을 쓴다. 조회는 응답의 status를 정할 때 읽고,
 * 해제는 읽지 않는다.
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

    /**
     * 내 선점 조회(spec 6.5). 오류(400은 컨트롤러의 경로 값 해석): 404 NOT_FOUND(선점이 없음, 다른 사용자의 선점, 해제·결제된 선점).
     *
     * <p>응답의 status는 현재 시각으로 판정한다(R3 지연 만료): 유효하면 ACTIVE, 만료면 EXPIRED. seats와 amount는 만든 때의 묶음이다.
     * 만료 뒤 다른 선점이 덮어쓴 좌석도 들어간다(결정 0070). 잠그지 않고 읽는다.
     */
    @Transactional(readOnly = true)
    public HoldDetail get(long userId, long holdId) {
        Hold hold = holdRepository.findWithSeatIdsById(holdId)
                .filter(found -> isOpenHoldOf(found, userId))
                .orElseThrow(HoldService::holdNotFound);
        Performance performance = performanceRepository.findWithVenueById(hold.getPerformanceId()).orElseThrow();
        List<Seat> seats = seatRepository.findAllById(hold.getSeatIds()).stream().sorted(SeatOrder.BY_ROW_AND_NUMBER).toList();
        return new HoldDetail(hold, performance, seats, hold.isValidAt(clock.instant()));
    }

    /**
     * 내 선점 해제(spec 6.6). 묶음 전체를 해제한다. 만료된 내 선점도 해제한다. 오류(400은 컨트롤러): 404 NOT_FOUND(선점이 없음,
     * 다른 사용자의 선점, 이미 해제·결제된 선점).
     *
     * <p>잠금 순서는 선점 행 → 좌석(id 오름차순)이다(spec 2.4, 결정 0100). 사용자 행은 잠그지 않고, 현재 시각을 쓰지 않는다.
     * <ol>
     *   <li>선점 행을 먼저 잠근 뒤 주인과 저장 상태를 본다. 같은 선점의 해제가 동시에 오면 나중 요청은 앞 요청이 커밋한 RELEASED를 읽고 404다.</li>
     *   <li>좌석을 잠근 뒤 holdId가 이 선점인 좌석만 AVAILABLE로 되돌린다. 만료 뒤 다른 사용자가 덮어쓴 좌석은 건드리지 않는다.
     *       그 선점이 커밋 전이면 기다렸다가 커밋한 holdId를 읽는다.</li>
     * </ol>
     */
    @Transactional
    public void release(long userId, long holdId) {
        Hold hold = holdRepository.lockById(holdId)
                .filter(found -> isOpenHoldOf(found, userId))
                .orElseThrow(HoldService::holdNotFound);
        List<Seat> seats = seatRepository.lockByPerformanceIdAndIdInOrderById(hold.getPerformanceId(), hold.getSeatIds());
        seats.stream()
                .filter(seat -> hold.getId().equals(seat.getHoldId()))
                .forEach(Seat::release);
        hold.release();
    }

    /**
     * 조회·해제할 수 있는 선점: 요청한 사용자의 선점이고 저장 상태가 ACTIVE다(만료된 선점 포함, spec 5·6.5·6.6).
     * 다른 사용자의 선점과 해제·결제된 선점은 없는 선점과 같게 404다.
     */
    private static boolean isOpenHoldOf(Hold hold, long userId) {
        return hold.getUserId() == userId && hold.getStatus() == HoldStatus.ACTIVE;
    }

    private static ApiException holdNotFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "선점을 찾을 수 없습니다.");
    }
}
