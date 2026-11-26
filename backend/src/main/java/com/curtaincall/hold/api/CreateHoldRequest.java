package com.curtaincall.hold.api;

import java.util.List;

import org.hibernate.validator.constraints.UniqueElements;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * spec 6.4 요청. 검증에 실패하면 400 INVALID_INPUT이다: performanceId가 없거나 양수가 아님, seatIds가 없거나 비었거나 중복이나 양수가
 * 아닌 값이 있음. 정수가 아닌 JSON 값(1.5, 1e3, "1")은 본문을 읽을 때 400이 된다(결정 0072).
 *
 * <p>좌석 수의 상한은 두지 않는다. 5석 이상은 400이 아니라 한도 검사에서 409 HOLD_LIMIT_EXCEEDED다(spec 6.4).
 */
public record CreateHoldRequest(
        @NotNull @Positive Long performanceId,
        @NotEmpty @UniqueElements List<@NotNull @Positive Long> seatIds) {
}
