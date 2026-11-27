package com.curtaincall.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R2 한도(spec 2.2 '보유 좌석 수'). 이 단계의 보유 좌석 수는 유효한 선점의 좌석만 센다. 예매 좌석은 결제 단계(5단계)에서 더한다.
 * 같은 공연의 선점만 넘기는 것은 호출하는 쪽(조회)의 몫이고, 다른 공연의 선점을 세지 않는 것은 통합 테스트에서 확인한다.
 */
class HoldLimitTest {

    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

    @Test
    @DisplayName("R2: 보유 3석이면 1석은 허용하고 2석은 거부한다")
    void threeHeldAllowsOneMoreSeatOnly() {
        assertThat(HoldLimit.allows(3, 1)).isTrue();
        assertThat(HoldLimit.allows(3, 2)).isFalse();
        // 4석 경계의 다른 쪽: 보유 0석이면 4석까지, 보유 4석이면 1석도 안 된다.
        assertThat(HoldLimit.allows(0, 4)).isTrue();
        assertThat(HoldLimit.allows(0, 5)).isFalse();
        assertThat(HoldLimit.allows(4, 1)).isFalse();
    }

    @Test
    @DisplayName("R2: 만료·해제된 선점은 보유 좌석 수에 넣지 않는다(결제된 선점의 좌석은 예매로 센다)")
    void onlyValidHoldsCount() {
        Hold valid = new Hold(7L, 30L, List.of(1L, 2L), HoldStatus.ACTIVE, NOW.minusSeconds(60));
        Hold expiredNow = new Hold(7L, 30L, List.of(3L), HoldStatus.ACTIVE, NOW.minus(Hold.VALIDITY));
        Hold released = new Hold(7L, 30L, List.of(4L), HoldStatus.RELEASED, NOW.minusSeconds(60));
        Hold paid = new Hold(7L, 30L, List.of(5L), HoldStatus.PAID, NOW.minusSeconds(60));

        assertThat(HoldLimit.heldSeatCount(List.of(valid, expiredNow, released, paid), NOW)).isEqualTo(2);
        assertThat(HoldLimit.heldSeatCount(List.of(expiredNow, released, paid), NOW)).isZero();
        assertThat(HoldLimit.heldSeatCount(List.of(), NOW)).isZero();
    }
}
