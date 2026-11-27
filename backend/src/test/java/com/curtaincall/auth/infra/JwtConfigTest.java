package com.curtaincall.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import com.curtaincall.support.TestTokens;

class JwtConfigTest {

    /**
     * 시스템 시계와 크게 다른 시각. 검증기가 주입한 시계 대신 시스템 시계를 쓰면 만료 판정이 달라지게 한다.
     * 토큰 검증의 빈 생성 코드는 스프링이 시작할 때 한 번만 돌아 PIT가 통합 테스트로는 재지 못한다(결정 0204). 여기서 직접 만들어 확인한다.
     */
    private static final Instant NOW = Instant.parse("2000-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SECRET = "unit-test-only-secret-at-least-32-bytes";

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
    @DisplayName("공백뿐인 서명 키는 32바이트 이상이어도 키를 만들지 않는다")
    void rejectsBlankSecretOfAnyLength() {
        assertThatThrownBy(() -> JwtConfig.signingKey(" ".repeat(JwtConfig.MIN_SECRET_BYTES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("없습니다");
    }

    @Test
    @DisplayName("32바이트 이상이면 HS256(HmacSHA256) 키를 만든다")
    void acceptsLongEnoughSecret() {
        SecretKey key = JwtConfig.signingKey("exactly-32-bytes-long-secret-key");

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(32);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "9223372036854775807"})
    @DisplayName("R6: sub가 1 이상의 long을 부호·앞자리 0 없이 쓴 값이면 사용자 id로 받는다")
    void acceptsUserIdSubject(String sub) {
        assertThat(JwtConfig.isUserId(sub)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "00", "-1", "+1", "01", " 1", "1 ", "1.0", "alice", "9223372036854775808"})
    @DisplayName("R6: sub가 없거나 0·음수·부호·앞자리 0·공백·소수·문자·long 범위 밖이면 사용자 id로 받지 않는다")
    void rejectsNonUserIdSubject(String sub) {
        assertThat(JwtConfig.isUserId(sub)).isFalse();
    }

    @Test
    @DisplayName("R6: 토큰 검증기는 주입한 시계로 만료를 판정한다(exp와 같은 시각은 허용, 1초 지나면 거부)")
    void validatorJudgesExpiryWithInjectedClock() {
        OAuth2TokenValidator<Jwt> validator = JwtConfig.validator(CLOCK);

        assertThat(validator.validate(jwt("42", NOW)).hasErrors()).isFalse();
        assertThat(validator.validate(jwt("42", NOW.minusSeconds(1))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("R6: 토큰 검증기는 exp가 없는 토큰과 sub가 사용자 id가 아닌 토큰을 거부한다")
    void validatorRejectsMissingExpiryAndNonUserIdSubject() {
        OAuth2TokenValidator<Jwt> validator = JwtConfig.validator(CLOCK);
        Jwt withoutExpiry = Jwt.withTokenValue("token").header("alg", "HS256").subject("42").issuedAt(NOW).build();

        assertThat(validator.validate(withoutExpiry).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("alice", NOW.plusSeconds(60))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("R6: 같은 키로 만든 인코더·디코더에서 정상 토큰은 통과하고, 서명이 맞아도 검증 규칙(sub, 만료)에 걸리면 거부한다")
    void decoderAppliesValidator() {
        JwtConfig config = new JwtConfig(SECRET);
        JwtEncoder encoder = config.jwtEncoder();
        JwtDecoder decoder = config.jwtDecoder(CLOCK);
        String valid = TestTokens.sign(encoder, 42L, NOW, NOW.plusSeconds(3600));
        String notUserId = TestTokens.sign(encoder, c -> c.subject("alice").issuedAt(NOW).expiresAt(NOW.plusSeconds(3600)));
        String expired = TestTokens.sign(encoder, 42L, NOW.minusSeconds(3600), NOW.minusSeconds(1));

        assertThat(decoder.decode(valid).getSubject()).isEqualTo("42");
        assertThatThrownBy(() -> decoder.decode(notUserId)).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtValidationException.class);
    }

    private static Jwt jwt(String subject, Instant expiresAt) {
        return Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(subject).issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).build();
    }
}
