package com.curtaincall.payment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * spec 6.7 요청 본문. holdId가 없거나 양수가 아니면 400 INVALID_INPUT이다. 정수가 아닌 JSON 값(1.5, 1e3, "1")은 본문을 읽을 때 400이 된다(결정 0072).
 */
public record PayRequest(@NotNull @Positive Long holdId) {
}
