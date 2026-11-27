package com.curtaincall.hold.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * 선점(spec 2.1). 한 공연의 좌석 묶음(1~4석)을 결제 전에 잡아 둔다(결정 0003).
 *
 * <ul>
 *   <li>{@code expiresAt}은 {@code createdAt} + 5분이고 늘리지 않는다(R3).</li>
 *   <li>만료는 저장하지 않는다. 유효한지는 {@link #isValidAt(Instant)}로 현재 시각과 비교해 판정한다(지연 만료).</li>
 *   <li>{@code seatIds}는 만든 때의 묶음이다. 좌석을 지금 잡은 선점은 좌석의 holdId로 판단한다(결정 0009). 만료된 선점의 좌석을
 *       새 선점이 덮어써도 이 목록에서 빼지 않는다.</li>
 *   <li>사용자와 공연은 id로만 가진다. 공연 정보는 응답을 만들 때 공연 조회로 읽는다.</li>
 * </ul>
 */
@Entity
@Table(name = "holds")
public class Hold {

    /** R3: 선점은 5분간 유효하다. */
    public static final Duration VALIDITY = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "performance_id", nullable = false)
    private long performanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection
    @CollectionTable(name = "hold_seats", joinColumns = @JoinColumn(name = "hold_id"))
    @Column(name = "seat_id", nullable = false)
    private Set<Long> seatIds = new HashSet<>();

    protected Hold() {
    }

    /** 선점 성공(spec 2.3): ACTIVE로 만든다. 좌석을 HELD로 바꾸는 것은 호출하는 쪽이 좌석을 잠근 뒤에 한다. */
    public static Hold create(long userId, long performanceId, Collection<Long> seatIds, Instant now) {
        return new Hold(userId, performanceId, seatIds, HoldStatus.ACTIVE, now);
    }

    /** 저장 상태를 정해 만든다. 다른 상태의 선점은 같은 패키지의 테스트가 판정 규칙을 확인할 때만 만든다. */
    Hold(long userId, long performanceId, Collection<Long> seatIds, HoldStatus status, Instant createdAt) {
        if (seatIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("좌석 id가 비었다: " + seatIds);
        }
        Set<Long> distinct = new HashSet<>(seatIds);
        if (distinct.size() != seatIds.size()) {
            throw new IllegalArgumentException("같은 좌석을 두 번 담을 수 없다: " + seatIds);
        }
        // 선점 1건이 4석을 넘으면 그것만으로 R2 한도를 넘는다.
        if (distinct.isEmpty() || distinct.size() > HoldLimit.MAX_SEATS_PER_PERFORMANCE) {
            throw new IllegalArgumentException("선점은 좌석 1~4석의 묶음이다: " + seatIds);
        }
        this.userId = userId;
        this.performanceId = performanceId;
        this.seatIds = distinct;
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = createdAt.plus(VALIDITY);
    }

    /**
     * spec 2.2 '유효한 선점': status가 ACTIVE이고 현재 시각 &lt; expiresAt. 현재 시각이 expiresAt과 같거나 이후면 만료다(R3).
     * 유효한지를 판정하는 곳은 여기 한 곳이다(R1 좌석 판정, R2 보유 좌석 수, 좌석의 API 상태가 모두 이것을 쓴다).
     */
    public boolean isValidAt(Instant now) {
        return status == HoldStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /**
     * 해제(spec 2.3): ACTIVE → RELEASED. 만료된 선점도 저장 상태는 ACTIVE이므로 해제한다(spec 6.6). 현재 시각을 쓰지 않는다.
     * 좌석은 호출하는 쪽이 선점 행과 좌석을 잠근 뒤, holdId가 이 선점인 좌석만 되돌린다(만료 뒤 다른 선점이 덮어쓴 좌석은 건드리지 않는다).
     * 만든 때의 좌석 묶음({@code seatIds})은 그대로 둔다.
     */
    public void release() {
        if (status != HoldStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 선점만 해제한다: " + status);
        }
        this.status = HoldStatus.RELEASED;
    }

    /**
     * 조회·해제·결제할 수 있는 선점: 요청한 사용자의 선점이고 저장 상태가 ACTIVE다(만료된 선점 포함, spec 6.5·6.6·6.7).
     * 다른 사용자의 선점과 해제·결제된 선점은 없는 선점과 같게 404다. 만료(결제의 409)는 따로 {@link #isValidAt}으로 판정한다.
     */
    public boolean isOpenFor(long userId) {
        return this.userId == userId && status == HoldStatus.ACTIVE;
    }

    /**
     * 결제 승인(spec 2.3): ACTIVE → PAID. judgedAt은 결제가 만료를 판정한 시각(모든 잠금 뒤에 읽은 현재 시각, spec 2.4)이고,
     * 그 시각에 유효해야 한다(R4). 판정을 통과했으면 모의 결제가 만료 시각을 넘겨 끝나도 결제를 마치므로(spec 2.4), 승인된 때가 아니라
     * 판정한 시각을 받는다. 좌석은 호출하는 쪽이 잠근 뒤 SOLD로 바꾼다. 만든 때의 좌석 묶음({@code seatIds})은 그대로 둔다.
     *
     * @throws IllegalStateException judgedAt에 유효한 선점이 아니면(만료, 해제·결제됨). 서비스가 앞에서 걸러 정상 흐름에서는 생기지 않는다.
     */
    public void pay(Instant judgedAt) {
        if (!isValidAt(judgedAt)) {
            throw new IllegalStateException("판정 시각에 유효한 선점만 결제한다: " + status + ", 만료 " + expiresAt + ", 판정 " + judgedAt);
        }
        this.status = HoldStatus.PAID;
    }

    /** spec 2.2 '결제 금액': 공연 가격 × 선점 좌석 수(만든 때의 묶음). */
    public long amount(int unitPrice) {
        return (long) unitPrice * seatIds.size();
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

    public HoldStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<Long> getSeatIds() {
        return Collections.unmodifiableSet(seatIds);
    }
}
