package com.curtaincall.reservation.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.curtaincall.hold.domain.Hold;
import com.curtaincall.hold.domain.HoldStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * 예매(spec 2.1). 결제가 승인된 선점 하나로 하나 만든다. 취소 기능은 없다(spec 8).
 *
 * <ul>
 *   <li>좌석은 선점의 좌석 묶음을 그대로 옮긴다. 결제된 선점의 좌석은 보유 좌석 수(R2)에서 예매로 한 번만 센다(spec 2.2).</li>
 *   <li>사용자·공연·선점은 id로만 가진다(선점과 같은 방식, 결정 0070). 공연 정보는 응답을 만들 때 공연 조회로 읽는다.</li>
 * </ul>
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "performance_id", nullable = false)
    private long performanceId;

    @Column(name = "hold_id", nullable = false)
    private long holdId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @ElementCollection
    @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
    @Column(name = "seat_id", nullable = false)
    private Set<Long> seatIds = new HashSet<>();

    protected Reservation() {
    }

    /**
     * 결제가 승인된 선점으로 예매를 만든다(spec 2.3). 선점은 이미 PAID여야 한다({@link Hold#pay}). 좌석은 선점의 묶음이다.
     *
     * @param amount 결제 금액(spec 2.2, {@link Hold#amount})
     * @param reservedAt 예매 시각. 결제가 만료를 판정한 시각이다(결정 0140)
     */
    public static Reservation of(Hold paidHold, long amount, Instant reservedAt) {
        if (paidHold.getStatus() != HoldStatus.PAID) {
            throw new IllegalStateException("결제된 선점으로만 예매를 만든다: " + paidHold.getStatus());
        }
        Reservation reservation = new Reservation();
        reservation.userId = paidHold.getUserId();
        reservation.performanceId = paidHold.getPerformanceId();
        reservation.holdId = Objects.requireNonNull(paidHold.getId(), "저장된 선점이어야 한다");
        reservation.amount = amount;
        reservation.reservedAt = Objects.requireNonNull(reservedAt);
        reservation.seatIds = new HashSet<>(paidHold.getSeatIds());
        return reservation;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getPerformanceId() {
        return performanceId;
    }

    public long getHoldId() {
        return holdId;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Set<Long> getSeatIds() {
        return Collections.unmodifiableSet(seatIds);
    }
}
