package com.curtaincall.hold.domain;

import java.time.Instant;
import java.util.Collection;

/**
 * R2: 한 사용자는 공연 하나에서 최대 4석(유효한 선점 + 예매)까지 가진다(spec 2.2 '보유 좌석 수').
 *
 * <p>보유 좌석 수는 유효한 선점의 좌석 + 예매의 좌석이다({@link #ownedSeatCount}). 결제된 선점(PAID)은 유효한 선점이 아니므로 선점 몫에서
 * 빠지고, 그 좌석은 예매 몫으로 한 번만 센다.
 */
public final class HoldLimit {

    public static final int MAX_SEATS_PER_PERFORMANCE = 4;

    private HoldLimit() {
    }

    /**
     * 보유 좌석 수 중 선점 몫: 넘겨받은 선점 중 현재 시각에 유효한 선점의 좌석 수. 만료·해제된 선점은 세지 않고,
     * 결제된 선점의 좌석은 예매로 한 번만 센다(spec 2.2). 같은 사용자·같은 공연의 선점만 넘기는 것은 호출하는 쪽의 몫이다.
     */
    public static int heldSeatCount(Collection<Hold> holds, Instant now) {
        return holds.stream()
                .filter(hold -> hold.isValidAt(now))
                .mapToInt(hold -> hold.getSeatIds().size())
                .sum();
    }

    /**
     * 보유 좌석 수(spec 2.2): 넘겨받은 선점 중 현재 시각에 유효한 선점의 좌석 수 + 예매 좌석 수. 같은 사용자·같은 공연의 선점과 예매 좌석 수만
     * 넘기는 것은 호출하는 쪽의 몫이다.
     */
    public static long ownedSeatCount(Collection<Hold> holds, long reservedSeats, Instant now) {
        return heldSeatCount(holds, now) + reservedSeats;
    }

    /** 보유 좌석 수 + 요청 좌석 수가 4 이하면 허용한다. 넘으면 409 HOLD_LIMIT_EXCEEDED(5석 이상을 한 번에 요청한 경우 포함). */
    public static boolean allows(long ownedSeats, int requestedSeats) {
        return ownedSeats + requestedSeats <= MAX_SEATS_PER_PERFORMANCE;
    }
}
