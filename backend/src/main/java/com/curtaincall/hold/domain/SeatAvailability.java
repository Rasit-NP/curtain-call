package com.curtaincall.hold.domain;

import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.curtaincall.performance.domain.Seat;
import com.curtaincall.performance.domain.SeatStatus;

/**
 * 좌석의 API 상태와 선점 가능 여부(spec 2.2). 좌석을 잡은 선점(holder)은 좌석의 holdId가 가리키는 선점이다(결정 0009).
 *
 * <table>
 *   <caption>저장 상태와 좌석을 잡은 선점 → API 상태</caption>
 *   <tr><th>저장 상태</th><th>좌석을 잡은 선점</th><th>API 상태</th></tr>
 *   <tr><td>AVAILABLE</td><td>없음</td><td>AVAILABLE</td></tr>
 *   <tr><td>HELD</td><td>유효</td><td>HELD</td></tr>
 *   <tr><td>HELD</td><td>만료</td><td>AVAILABLE</td></tr>
 *   <tr><td>SOLD</td><td>(결제됨)</td><td>SOLD</td></tr>
 * </table>
 */
public final class SeatAvailability {

    private SeatAvailability() {
    }

    /**
     * 저장 상태가 HELD라도 좌석을 잡은 선점이 유효하지 않으면(만료) AVAILABLE이다. HELD 좌석은 좌석을 잡은 선점과 함께 판정해야 한다.
     * 빠뜨리면 선점된 좌석이 AVAILABLE로 보여 R1이 깨지므로, 조용히 넘어가지 않고 예외를 던진다.
     */
    public static SeatStatus apiStatus(SeatStatus stored, @Nullable Hold holder, Instant now) {
        if (stored != SeatStatus.HELD) {
            return stored;
        }
        if (holder == null) {
            throw new IllegalArgumentException("HELD 좌석은 좌석을 잡은 선점(holdId)과 함께 판정한다");
        }
        return holder.isValidAt(now) ? SeatStatus.HELD : SeatStatus.AVAILABLE;
    }

    /** R1: 유효한 선점이 없고 판매되지 않은 좌석(API 상태가 AVAILABLE)만 선점할 수 있다. 내 유효한 선점의 좌석도 선점할 수 없다. */
    public static boolean isHoldable(SeatStatus stored, @Nullable Hold holder, Instant now) {
        return apiStatus(stored, holder, now) == SeatStatus.AVAILABLE;
    }

    /** {@link #apiStatus(SeatStatus, Hold, Instant)}를 좌석과, 좌석들을 잡은 선점(id → 선점)으로 부른다. */
    public static SeatStatus apiStatus(Seat seat, Map<Long, Hold> holdersById, Instant now) {
        Long holdId = seat.getHoldId();
        return apiStatus(seat.getStatus(), holdId == null ? null : holdersById.get(holdId), now);
    }

    /** {@link #isHoldable(SeatStatus, Hold, Instant)}를 좌석과, 좌석들을 잡은 선점(id → 선점)으로 부른다. */
    public static boolean isHoldable(Seat seat, Map<Long, Hold> holdersById, Instant now) {
        return apiStatus(seat, holdersById, now) == SeatStatus.AVAILABLE;
    }
}
