package com.curtaincall.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.auth.domain.UserRepository;
import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.hold.domain.Hold;
import com.curtaincall.hold.domain.HoldRepository;
import com.curtaincall.payment.application.PaymentOutcome.Approved;
import com.curtaincall.payment.application.PaymentOutcome.Rejected;
import com.curtaincall.payment.domain.IdempotencyRecord;
import com.curtaincall.payment.domain.IdempotencyRecordRepository;
import com.curtaincall.payment.domain.Payment;
import com.curtaincall.payment.domain.PaymentGateway;
import com.curtaincall.payment.domain.PaymentRepository;
import com.curtaincall.payment.domain.StoredResponse;
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
 * <p>동시성은 DB 행 잠금과 유니크 제약으로 보장한다(spec 2.4, 결정 0100, 0151). JVM 안의 락은 쓰지 않는다. 잠금 순서는 요청한 사용자 행 →
 * 같은 키의 멱등 기록 → 선점 행 → 좌석(id 오름차순)이고, 현재 시각(만료 판정)은 모든 잠금 뒤에 읽는다.
 * <ol>
 *   <li>사용자 행: 같은 사용자의 선점 생성·결제를 한 줄로 세운다(R2). 판정을 통과한 결제가 만료 시각을 넘겨 커밋돼도, 같은 사용자의 선점 생성은
 *       결제가 끝난 뒤에 보유 좌석 수를 센다.</li>
 *   <li>같은 키의 멱등 기록(R5): (사용자, 키) 유니크 제약에 기록을 넣어 키를 잡는다. 같은 키 요청은 먼저 요청이 끝날 때까지 기다렸다가 그 결과를 받는다.
 *       같은 사용자의 결제는 이미 사용자 행에서 한 줄로 서지만, 키를 잡는 것도 DB가 보장하게 둔다.</li>
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
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public PaymentService(Clock clock, UserRepository userRepository, HoldRepository holdRepository, SeatRepository seatRepository,
            PerformanceRepository performanceRepository, ReservationRepository reservationRepository,
            PaymentRepository paymentRepository, PaymentGateway paymentGateway, IdempotencyRecordRepository idempotencyRecordRepository) {
        this.clock = clock;
        this.userRepository = userRepository;
        this.holdRepository = holdRepository;
        this.seatRepository = seatRepository;
        this.performanceRepository = performanceRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    /**
     * 검증(400)을 통과한 결제 요청을 멱등 키(R5, spec 6.7.1)와 함께 처리하고, 응답(상태 코드와 본문)을 돌려준다. 한 트랜잭션이다.
     *
     * <ul>
     *   <li>같은 사용자가 이 키를 처음 쓰면 결제를 처리하고({@link #process}), 결과를 toResponse로 바꾼 응답을 멱등 기록에 저장해 함께 커밋한다.
     *       결과가 201·402·404·409 중 무엇이든 저장한다.</li>
     *   <li>이 키에 저장한 응답이 있고 선점(holdId)이 같으면 다시 처리하지 않고(모의 결제도 부르지 않고) 저장한 응답을 그대로 돌려준다.
     *       그 사이 선점이 결제·해제·만료됐어도 같다.</li>
     *   <li>선점이 다르면 400 INVALID_INPUT(ApiException)이고 아무것도 처리하지 않는다.</li>
     *   <li>같은 키 요청이 처리 중이면 끝날 때까지 기다린다. 그 요청이 500으로 끝나 되돌려졌으면 이 요청이 처음 온 요청처럼 처리한다.</li>
     * </ul>
     *
     * <p>응답의 모양(본문)은 API 계층이 정하므로 toResponse로 받는다. 저장은 같은 트랜잭션에서 해야 하므로 서비스 안에서 부른다(결정 0151).
     * 예상하지 못한 오류(500)는 트랜잭션을 되돌리므로 기록도 남지 않는다.
     *
     * @param toResponse 결제 결과를 저장할 응답으로 바꾼다. 처음 응답과 다시 보낸 요청의 응답은 모두 이 값으로 만든다.
     */
    @Transactional
    public StoredResponse pay(long userId, long holdId, String idempotencyKey, Function<PaymentOutcome, StoredResponse> toResponse) {
        // 토큰의 사용자 행이 없으면 잠글 행이 없다. 따로 처리하지 않는다(결정 0071): 아래에서 기록을 넣을 때 외래 키가 막아 500이다.
        userRepository.lockById(userId);

        // 같은 사용자·같은 키의 멱등 기록을 잡는다: 사용자 행 뒤, 선점 행 앞(spec 2.4, 결정 0100, 0151). 기록의 생성 시각은 판정에 쓰지 않는다.
        boolean claimed = idempotencyRecordRepository.claim(userId, idempotencyKey, holdId, clock.instant()) == 1;
        IdempotencyRecord record = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("잡은 멱등 기록을 읽지 못했다"));
        if (!claimed) {
            if (!record.isFor(holdId)) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "이 Idempotency-Key는 다른 선점의 결제에 이미 쓰였습니다.");
            }
            return record.response();
        }

        StoredResponse response = toResponse.apply(process(userId, holdId));
        record.complete(response);
        return response;
    }

    /**
     * 결제 요청을 처리한다. 사용자 행과 멱등 기록은 {@link #pay}가 이미 잡았다. 결과(spec 6.7 오류 표 4~6번과 승인)를 돌려주고, 트랜잭션은 커밋된다.
     * <ul>
     *   <li>404 NOT_FOUND: 선점이 없음, 다른 사용자의 선점, 해제·결제된 선점. 아무것도 바꾸지 않는다.</li>
     *   <li>409 HOLD_EXPIRED: 잠근 뒤의 현재 시각 ≥ expiresAt(R4). 모의 결제를 부르지 않고 아무것도 바꾸지 않는다.</li>
     *   <li>402 PAYMENT_FAILED: 모의 결제 거절. 결제 FAILED만 남기고 선점·좌석·만료 시각은 그대로다.</li>
     *   <li>승인(201): 선점 PAID, 좌석 SOLD(holdId 비움), 예매, 결제 SUCCEEDED. 예매 시각은 판정 시각이다.</li>
     * </ul>
     */
    private PaymentOutcome process(long userId, long holdId) {
        Optional<Hold> locked = holdRepository.lockById(holdId).filter(hold -> hold.isOpenFor(userId));
        if (locked.isEmpty()) {
            // 선점 조회·해제의 404(HoldService)와 같은 설명을 쓴다.
            return new Rejected(ErrorCode.NOT_FOUND, "선점을 찾을 수 없습니다.");
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
