package com.curtaincall.auth.infra;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 액세스 토큰(spec 3.2)의 발급·검증. HS256, 서명 키는 {@code curtaincall.jwt.secret}.
 *
 * <ul>
 *   <li>서명 키: local·e2e·test는 application.yml의 개발 전용 키, prod는 환경변수 JWT_SECRET만 쓴다.
 *       값이 없거나 32바이트(HS256 키 길이)보다 짧으면 이 설정이 실패해 애플리케이션이 시작되지 않는다.</li>
 *   <li>만료는 주입한 Clock으로 판정하고 허용 오차는 0이다. exp와 같은 시각의 토큰은 유효하다(JwtTimestampValidator).</li>
 *   <li>exp가 없거나, sub가 사용자 id(1 이상의 long)가 아닌 토큰은 거부한다. 컨트롤러는 sub를 그대로 id로 쓴다(@CurrentUserId).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /** HS256 키는 256비트 이상이어야 한다. */
    static final int MIN_SECRET_BYTES = 32;

    private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    private final SecretKey signingKey;

    JwtConfig(@Value("${curtaincall.jwt.secret}") String secret) {
        this.signingKey = signingKey(secret);
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(signingKey).algorithm(ALGORITHM).build();
    }

    @Bean
    JwtDecoder jwtDecoder(Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(ALGORITHM).build();
        decoder.setJwtValidator(validator(clock));
        return decoder;
    }

    /**
     * JwtValidators.createDefault()를 쓰지 않는다. 그 기본 시각 검증기는 시스템 시계와 60초 허용 오차를 쓰기 때문이다.
     * 직접 서명한 HS256 토큰이라 그 밖의 기본 검증기(인증서 바인딩 cnf 등)는 필요 없다.
     */
    static OAuth2TokenValidator<Jwt> validator(Clock clock) {
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        timestamp.setAllowEmptyExpiryClaim(false);
        JwtClaimValidator<Object> numericSubject = new JwtClaimValidator<>(JwtClaimNames.SUB, JwtConfig::isUserId);
        return new DelegatingOAuth2TokenValidator<>(timestamp, numericSubject);
    }

    /** sub가 사용자 id로 쓸 수 있는 값(1 이상의 long, 앞자리 0이나 부호 없음)인지. */
    static boolean isUserId(Object sub) {
        if (!(sub instanceof String value) || !value.matches("[1-9][0-9]*")) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static SecretKey signingKey(@Nullable String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 서명 키(curtaincall.jwt.secret, prod는 JWT_SECRET)가 없습니다.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT 서명 키는 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
