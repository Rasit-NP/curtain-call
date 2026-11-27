package com.curtaincall.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.auth.domain.UserRepository;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.hold.domain.Hold;
import com.curtaincall.hold.domain.HoldRepository;
import com.curtaincall.payment.application.PaymentOutcome.Approved;
import com.curtaincall.payment.application.PaymentOutcome.Rejected;
import com.curtaincall.payment.domain.Payment;
import com.curtaincall.payment.domain.PaymentGateway;
import com.curtaincall.payment.domain.PaymentRepository;
import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.PerformanceRepository;
import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatOrder;
import com.curtaincall.performance.domain.SeatRepository;
import com.curtaincall.reservation.application.ReservationDetail;
import com.curtaincall.reservation.domain.Reservation;
import com.curtaincall.reservation.domain.ReservationRepository;

/**
 * 결제(spec 6.7). 선점 1건을 모의 결제하고, 승인되면 예매를 만든다. 한 트랜잭션(READ COMMITTED)에서 처리한다.
 *
 * <p>동시성은 DB 행 잠금으로 보장한다(spec 2.4, 결정 0100). JVM 안의 락은 쓰지 않는다. 잠금 순서는 요청한 사용자 행 → (같은 키의 멱등 기록, #56)
 * → 선점 행 → 좌석(id 오름차순)이고, 현재 시각은 모든 잠금 뒤에 읽는다.
 * <ol>
 *   <li>사용자 행: 같은 사용자의 선점 생성·결제를 한 줄로 세운다(R2). 판정을 통과한 결제가 만료 시각을 넘겨 커밋돼도, 같은 사용자의 선점 생성은
 *       결제가 끝난 뒤에 보유 좌석 수를 센다.</li>
 *   <li>선점 행: 같은 선점의 결제·해제를 한 줄로 세운다. 나중 요청은 앞 요청이 커밋한 PAID·RELEASED를 읽고 404다.</li>
 *   <li>좌석: 결제를 처리하는 동안 만료 시각이 지나도 다른 선점이 그 좌석을 가져가지 못한다(R4). 선점 생성·해제와 같은 쿼리로 같은 순서로 잠근다.</li>
 *   <li>현재 시각: 잠금을 기다리는 동안 만료 시각이 지났으면 만료로 판정한다. 판정을 통과했으면 모의 결제가 늦게 끝나도 결제를 마친다.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private final Clock clock;
    private final UserRepository userRepository;
    private final HoldRepository holdRepository;
    private final SeatRepository seatRepository;
    private final PerformanceRepository performanceRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(Clock clock, UserRepository userRepository, HoldRepository holdRepository, SeatRepository seatRepository,
            PerformanceRepository performanceRepository, ReservationRepository reservationRepository,
            PaymentRepository paymentRepository, PaymentGateway paymentGateway) {
        this.clock = clock;
        this.userRepository = userRepository;
        this.holdRepository = holdRepository;
        this.seatRepository = seatRepository;
        this.performanceRepository = performanceRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * 검증(400)을 통과한 결제 요청을 처리한다. 결과(spec 6.7 오류 표 4~6번과 승인)를 돌려주고 트랜잭션을 커밋한다.
     * <ul>
     *   <li>404 NOT_FOUND: 선점이 없음, 다른 사용자의 선점, 해제·결제된 선점. 아무것도 바꾸지 않는다.</li>
     *   <li>409 HOLD_EXPIRED: 잠근 뒤의 현재 시각 ≥ expiresAt(R4). 모의 결제를 부르지 않고 아무것도 바꾸지 않는다.</li>
     *   <li>402 PAYMENT_FAILED: 모의 결제 거절. 결제 FAILED만 남기고 선점·좌석·만료 시각은 그대로다.</li>
     *   <li>승인(201): 선점 PAID, 좌석 SOLD(holdId 비움), 예매, 결제 SUCCEEDED. 예매 시각은 판정 시각이다.</li>
     * </ul>
     */
    @Transactional
    public PaymentOutcome pay(long userId, long holdId) {
        // 토큰의 사용자 행이 없으면 잠글 행이 없다(결정 0071). 그런 사용자에게는 선점이 없으므로 아래에서 404가 된다.
        userRepository.lockById(userId);

        // (#56) 같은 사용자·같은 키의 멱등 기록은 여기서 잡는다: 사용자 행 뒤, 선점 행 앞(spec 2.4, 결정 0100).

        Optional<Hold> locked = holdRepository.lockById(holdId).filter(hold -> hold.isOpenFor(userId));
        if (locked.isEmpty()) {
            return Rejected.of(ErrorCode.NOT_FOUND);
        }
        Hold hold = locked.get();
        List<Seat> seats = seatRepository.lockByPerformanceIdAndIdInOrderById(hold.getPerformanceId(), hold.getSeatIds());

        Instant now = clock.instant();
        if (!hold.isValidAt(now)) {
            return Rejected.of(ErrorCode.HOLD_EXPIRED);
        }

        Performance performance = performanceRepository.findWithVenueById(hold.getPerformanceId()).orElseThrow();
        long amount = hold.amount(performance.getPrice());
        PaymentGateway.Result result = paymentGateway.pay(new PaymentGateway.Request(userId, hold.getId(), amount));
        if (result == PaymentGateway.Result.DECLINED) {
            paymentRepository.save(Payment.failed(userId, hold.getId(), amount, now));
            return Rejected.of(ErrorCode.PAYMENT_FAILED);
        }

        seats.forEach(seat -> seat.sell(hold.getId()));
        hold.pay(now);
        Reservation reservation = reservationRepository.save(Reservation.of(hold, amount, now));
        Payment payment = paymentRepository.save(Payment.succeeded(userId, hold.getId(), amount, reservation.getId(), now));
        List<Seat> sortedSeats = seats.stream().sorted(SeatOrder.BY_ROW_AND_NUMBER).toList();
        return new Approved(payment.getId(), amount, new ReservationDetail(reservation, performance, sortedSeats));
    }
}
