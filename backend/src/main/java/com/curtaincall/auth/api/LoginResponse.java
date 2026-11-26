package com.curtaincall.auth.api;

/** spec 6.1 응답. expiresIn은 초 단위. */
public record LoginResponse(String accessToken, long expiresIn) {
}
