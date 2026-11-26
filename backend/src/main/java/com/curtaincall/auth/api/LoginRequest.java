package com.curtaincall.auth.api;

import jakarta.validation.constraints.NotBlank;

/** spec 6.1 요청. 없거나 비었거나 공백뿐이면 400 INVALID_INPUT. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
