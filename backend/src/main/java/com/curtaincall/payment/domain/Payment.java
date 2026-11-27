package com.curtaincall.payment.domain;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 결제(spec 2.1). 모의 결제를 부를 때마다 하나 생긴다. 승인되면 SUCCEEDED이고 예매(reservationId)가 있고, 거절되면 FAILED이고 예매가 없다
 * (DB 제약). 만료·없는 선점으로 거부한 요청은 모의 결제를 부르지 않으므로 결제가 생기지 않는다.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "hold_id", nullable = false)
    private long holdId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "reservation_id")
    private @Nullable Long reservationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {
    }

    private Payment(long userId, long holdId, long amount, PaymentStatus status, @Nullable Long reservationId, Instant createdAt) {
        this.userId = userId;
        this.holdId = holdId;
        this.amount = amount;
        this.status = status;
        this.reservationId = reservationId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** 결제 승인(spec 2.3): SUCCEEDED와 만든 예매. */
    public static Payment succeeded(long userId, long holdId, long amount, long reservationId, Instant createdAt) {
        return new Payment(userId, holdId, amount, PaymentStatus.SUCCEEDED, reservationId, createdAt);
    }

    /** 결제 거절(spec 2.3): FAILED이고 예매가 없다. */
    public static Payment failed(long userId, long holdId, long amount, Instant createdAt) {
        return new Payment(userId, holdId, amount, PaymentStatus.FAILED, null, createdAt);
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getHoldId() {
        return holdId;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public @Nullable Long getReservationId() {
        return reservationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
