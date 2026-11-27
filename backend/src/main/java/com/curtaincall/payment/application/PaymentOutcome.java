package com.curtaincall.payment.application;

import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.reservation.application.ReservationDetail;

/**
 * 검증(spec 6.7 오류 표 2번)을 통과한 결제 요청의 결과. 결제 서비스는 이 결과를 돌려주고 트랜잭션을 커밋한다. 그래서 거절(402)이어도 결제 FAILED가
 * 남는다. 예상하지 못한 오류(500)만 예외로 끝나 되돌려진다.
 *
 * <p>이 결과(201, 402, 404, 409)는 응답으로 바뀌어 같은 트랜잭션에서 멱등 기록에 저장된다(R5, spec 6.7.1, {@link PaymentService#pay}).
 */
public sealed interface PaymentOutcome {

    /** 승인(201): 결제 id, 결제 금액, 만든 예매. */
    record Approved(long paymentId, long amount, ReservationDetail reservation) implements PaymentOutcome {
    }

    /** 거부(404 NOT_FOUND, 409 HOLD_EXPIRED)나 거절(402 PAYMENT_FAILED). 거부는 아무것도 바꾸지 않았고, 거절은 결제 FAILED만 남겼다. */
    record Rejected(ErrorCode code, String detail) implements PaymentOutcome {

        static Rejected of(ErrorCode code) {
            return new Rejected(code, code.defaultDetail());
        }
    }
}
