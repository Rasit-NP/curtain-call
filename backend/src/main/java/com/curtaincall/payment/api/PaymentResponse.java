package com.curtaincall.payment.api;

import com.curtaincall.payment.application.PaymentOutcome;
import com.curtaincall.reservation.api.ReservationResponse;

/** spec 6.7 결제 승인 응답(201): {@code { paymentId, amount, reservation: Reservation }}. */
public record PaymentResponse(long paymentId, long amount, ReservationResponse reservation) {

    public static PaymentResponse from(PaymentOutcome.Approved approved) {
        return new PaymentResponse(approved.paymentId(), approved.amount(), ReservationResponse.from(approved.reservation()));
    }
}
