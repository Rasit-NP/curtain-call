package com.curtaincall.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtConfigTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "too-short-secret-31-bytes-long!"})
    @DisplayName("서명 키가 없거나 32바이트보다 짧으면 키를 만들지 않아 애플리케이션이 시작되지 않는다")
    void rejectsMissingOrShortSecret(String secret) {
        assertThatThrownBy(() -> JwtConfig.signingKey(secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT");
    }

    @Test
    @DisplayName("32바이트 이상이면 HS256(HmacSHA256) 키를 만든다")
    void acceptsLongEnoughSecret() {
        SecretKey key = JwtConfig.signingKey("exactly-32-bytes-long-secret-key");

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(32);
    }
}
