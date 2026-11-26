package com.curtaincall.auth.application;

/** 발급한 액세스 토큰과 유효 시간(초). */
public record AccessToken(String value, long expiresInSeconds) {
}
