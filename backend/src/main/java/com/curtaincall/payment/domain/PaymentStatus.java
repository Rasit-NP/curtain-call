package com.curtaincall.payment.domain;

/** 결제의 상태(spec 2.1). 승인되면 SUCCEEDED, 모의 결제가 거절하면 FAILED. */
public enum PaymentStatus {
    SUCCEEDED,
    FAILED
}
