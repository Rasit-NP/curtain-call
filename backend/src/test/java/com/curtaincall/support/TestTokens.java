package com.curtaincall.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 토큰 검증(서명, 형식, 만료) 테스트용으로 실제로 서명한 토큰을 만든다.
 * 운영과 같은 키로 서명할 때는 애플리케이션의 {@link JwtEncoder} 빈을 받아 쓴다.
 */
public final class TestTokens {

    /** 운영·개발 키와 다른 키. 서명이 다른 토큰을 만든다. */
    private static final String OTHER_KEY = "other-key-for-signature-mismatch-tests-only-0123456789";

    private TestTokens() {
    }

    /** 운영과 같은 키(HS256)로 서명한다. claims로 sub, iat, exp 등을 정한다. */
    public static String sign(JwtEncoder encoder, Consumer<JwtClaimsSet.Builder> claims) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder();
        claims.accept(builder);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    /** sub와 exp만 정한 정상 형식의 토큰. */
    public static String sign(JwtEncoder encoder, long userId, Instant issuedAt, Instant expiresAt) {
        return sign(encoder, c -> c.subject(String.valueOf(userId)).issuedAt(issuedAt).expiresAt(expiresAt));
    }

    /** 다른 키로 서명한 토큰. 그 밖의 내용은 정상이다. */
    public static String signWithOtherKey(long userId, Instant issuedAt, Instant expiresAt) {
        SecretKeySpec key = new SecretKeySpec(OTHER_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return sign(NimbusJwtEncoder.withSecretKey(key).build(), userId, issuedAt, expiresAt);
    }

    /** 서명이 없는 토큰(alg: none). */
    public static String unsigned(long userId, Instant expiresAt) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String header = base64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString(("{\"sub\":\"" + userId + "\",\"exp\":" + expiresAt.getEpochSecond() + "}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
