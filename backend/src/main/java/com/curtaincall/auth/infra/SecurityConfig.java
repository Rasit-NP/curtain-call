package com.curtaincall.auth.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

/**
 * 보안 설정(spec 3.1, 4, 6.9, R6). 인증 필터를 직접 만들지 않고 OAuth2 Resource Server의 JWT 지원을 쓴다.
 *
 * <ul>
 *   <li>인증 없이 열리는 경로: POST /api/auth/login, GET /api/performances/**, GET /actuator/health.
 *       e2e 프로필에서만 POST /api/test-support/reset도 연다. 나머지는 모두 인증이 필요하다.</li>
 *   <li>토큰이 없거나 잘못되면(공개 경로에 잘못된 토큰을 붙인 경우 포함) {@link AuthRequiredEntryPoint}가 401 AUTH_REQUIRED.</li>
 *   <li>세션을 만들지 않고(STATELESS) CSRF를 끈다. 쿠키로 인증하지 않는다.</li>
 *   <li>서블릿 컨테이너의 오류 전달(ERROR 디스패치, /error)은 인증 검사 없이 지나게 한다. 막으면 공개 경로에서 난
 *       400·500 같은 오류가 /error에서 401로 바뀐다. 오류 형식은 common.api.ProblemErrorController가 맞춘다.</li>
 * </ul>
 *
 * <p>main에 병합된 뒤 이 설정을 바꿔야 하면 사람에게 넘긴다(결정 0010).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    static final String E2E_RESET_PATH = "/api/test-support/reset";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthRequiredEntryPoint entryPoint, Environment environment)
            throws Exception {
        boolean e2e = environment.acceptsProfiles(Profiles.of("e2e"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/performances/**", "/actuator/health").permitAll();
                    if (e2e) {
                        auth.requestMatchers(HttpMethod.POST, E2E_RESET_PATH).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
