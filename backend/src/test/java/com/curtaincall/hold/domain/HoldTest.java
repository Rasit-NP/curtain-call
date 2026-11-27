package com.curtaincall.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 선점의 만료(R3, spec 2.1, 2.2)와 묶음 크기(spec 2.1). 스프링 컨텍스트 없이 시각을 값으로 넘긴다. */
class HoldTest {

    private static final Instant CREATED_AT = Instant.parse("2026-12-01T10:00:00Z");

    @Test
    @DisplayName("R3: 선점의 만료 시각은 만든 시각 + 5분이다")
    void expiresFiveMinutesAfterCreation() {
        Hold hold = Hold.create(7L, 30L, List.of(101L, 104L), CREATED_AT);

        assertThat(hold.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(hold.getExpiresAt()).isEqualTo(Instant.parse("2026-12-01T10:05:00Z"));
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.getUserId()).isEqualTo(7L);
        assertThat(hold.getPerformanceId()).isEqualTo(30L);
        assertThat(hold.getSeatIds()).containsExactlyInAnyOrder(101L, 104L);
    }

    @Test
    @DisplayName("R3: 현재 시각이 만료 시각 1초 전이면 유효하고, 같거나 이후면 만료로 판정한다")
    void validOnlyBeforeExpiresAt() {
        Hold hold = Hold.create(7L, 30L, List.of(101L), CREATED_AT);
        Instant expiresAt = hold.getExpiresAt();

        assertThat(hold.isValidAt(CREATED_AT)).isTrue();
        assertThat(hold.isValidAt(expiresAt.minusSeconds(1))).isTrue();
        assertThat(hold.isValidAt(expiresAt.minusNanos(1))).isTrue();
        assertThat(hold.isValidAt(expiresAt)).isFalse();
        assertThat(hold.isValidAt(expiresAt.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("해제·결제된 선점은 만료 시각 전이라도 유효하지 않다(유효한 선점은 ACTIVE이고 만료 전)")
    void releasedOrPaidHoldIsNotValid() {
        Instant beforeExpiry = CREATED_AT.plusSeconds(60);

        assertThat(new Hold(7L, 30L, List.of(101L), HoldStatus.RELEASED, CREATED_AT).isValidAt(beforeExpiry)).isFalse();
        assertThat(new Hold(7L, 30L, List.of(101L), HoldStatus.PAID, CREATED_AT).isValidAt(beforeExpiry)).isFalse();
    }

    @Test
    @DisplayName("선점을 해제하면 저장 상태가 RELEASED가 되어 만료 시각 전이라도 유효하지 않다")
    void releaseMakesHoldReleased() {
        // 해제는 현재 시각을 받지 않는다. 만료된 선점의 해제는 통합 테스트(releasingMyExpiredHold)가 본다.
        Hold hold = Hold.create(7L, 30L, List.of(101L), CREATED_AT);

        hold.release();

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(hold.isValidAt(CREATED_AT)).isFalse();
        // 만든 때의 좌석 묶음은 그대로다.
        assertThat(hold.getSeatIds()).containsExactly(101L);
    }

    @Test
    @DisplayName("ACTIVE가 아닌 선점(해제·결제됨)은 해제할 수 없다")
    void onlyActiveHoldCanBeReleased() {
        assertThatThrownBy(() -> new Hold(7L, 30L, List.of(101L), HoldStatus.RELEASED, CREATED_AT).release())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Hold(7L, 30L, List.of(101L), HoldStatus.PAID, CREATED_AT).release())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("선점은 서로 다른 좌석 1~4석의 묶음으로만 만든다(0석, 5석, 같은 좌석 두 번은 거부)")
    void holdIsBundleOfOneToFourDistinctSeats() {
        assertThatThrownBy(() -> Hold.create(7L, 30L, List.of(), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hold.create(7L, 30L, List.of(1L, 2L, 3L, 4L, 5L), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hold.create(7L, 30L, List.of(1L, 1L), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> Hold.create(7L, 30L, List.of(1L), CREATED_AT)).doesNotThrowAnyException();
        assertThatCode(() -> Hold.create(7L, 30L, List.of(1L, 2L, 3L, 4L), CREATED_AT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R4: 만료 시각 1초 전에는 결제할 수 있고, 같은 순간부터는 결제할 수 없다고 판정한다")
    void payableOnlyWhenJudgedBeforeExpiresAt() {
        // 결제는 모든 잠금 뒤에 읽은 현재 시각(판정 시각)을 넘긴다. 그 시각에 만료됐으면 PAID로 바꾸지 않는다.
        Hold judgedBeforeExpiry = Hold.create(7L, 30L, List.of(101L), CREATED_AT);
        judgedBeforeExpiry.pay(judgedBeforeExpiry.getExpiresAt().minusSeconds(1));
        assertThat(judgedBeforeExpiry.getStatus()).isEqualTo(HoldStatus.PAID);

        Hold judgedAtExpiry = Hold.create(7L, 30L, List.of(101L), CREATED_AT);
        assertThatThrownBy(() -> judgedAtExpiry.pay(judgedAtExpiry.getExpiresAt())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> judgedAtExpiry.pay(judgedAtExpiry.getExpiresAt().plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(judgedAtExpiry.getStatus()).isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    @DisplayName("결제하면 저장 상태가 PAID가 되어 만료 시각 전이라도 유효하지 않고, ACTIVE가 아닌 선점(해제·결제됨)은 결제할 수 없다")
    void payMakesHoldPaid() {
        Hold hold = Hold.create(7L, 30L, List.of(101L, 102L), CREATED_AT);

        hold.pay(CREATED_AT.plusSeconds(60));

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.PAID);
        assertThat(hold.isValidAt(CREATED_AT.plusSeconds(61))).isFalse();
        assertThat(hold.getSeatIds()).containsExactlyInAnyOrder(101L, 102L);
        assertThatThrownBy(() -> hold.pay(CREATED_AT.plusSeconds(62))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Hold(7L, 30L, List.of(101L), HoldStatus.RELEASED, CREATED_AT).pay(CREATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제 금액은 공연 가격 × 선점 좌석 수다")
    void amountIsPriceTimesSeatCount() {
        assertThat(Hold.create(7L, 30L, List.of(101L, 102L, 103L), CREATED_AT).amount(30000)).isEqualTo(90000L);
        assertThat(Hold.create(7L, 30L, List.of(101L), CREATED_AT).amount(0)).isZero();
        // 가격 × 4석이 int 범위를 넘어도 넘치지 않는다.
        assertThat(Hold.create(7L, 30L, List.of(1L, 2L, 3L, 4L), CREATED_AT).amount(Integer.MAX_VALUE))
                .isEqualTo(4L * Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("조회·해제·결제할 수 있는 선점은 요청한 사용자의 ACTIVE 선점이다(만료 포함, 해제·결제됨과 다른 사용자의 선점 제외)")
    void openOnlyForOwnerWhileActive() {
        assertThat(Hold.create(7L, 30L, List.of(101L), CREATED_AT).isOpenFor(7L)).isTrue();
        assertThat(Hold.create(7L, 30L, List.of(101L), CREATED_AT.minus(Hold.VALIDITY)).isOpenFor(7L)).isTrue();
        assertThat(Hold.create(7L, 30L, List.of(101L), CREATED_AT).isOpenFor(8L)).isFalse();
        assertThat(new Hold(7L, 30L, List.of(101L), HoldStatus.RELEASED, CREATED_AT).isOpenFor(7L)).isFalse();
        assertThat(new Hold(7L, 30L, List.of(101L), HoldStatus.PAID, CREATED_AT).isOpenFor(7L)).isFalse();
    }
}
