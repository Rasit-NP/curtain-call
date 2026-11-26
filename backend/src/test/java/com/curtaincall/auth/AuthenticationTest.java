package com.curtaincall.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.MutableClock;
import com.curtaincall.support.TestTokens;
import com.jayway.jsonpath.JsonPath;

/**
 * R6와 보안 설정(spec 3.1, 3.2, 4, 6.9). 토큰 검증은 실제로 서명한 토큰으로 한다({@code jwt()}는 해석을 건너뛴다).
 *
 * <p>기능 API가 아직 없어, 인증을 통과했는지는 "등록되지 않은 비공개 경로가 토큰 없이 401, 유효한 토큰이면 404"로 확인한다.
 * 기능 Issue에서 경로가 생겨도 기대값이 바뀌지 않도록 앞으로도 없을 경로를 쓴다.
 */
class AuthenticationTest extends IntegrationTest {

    /** 비공개(인증 필요)이고 앞으로도 등록하지 않을 경로. */
    private static final String UNREGISTERED_PRIVATE = "/api/no-such-path";
    /** 공개(GET /api/performances/**)이고 앞으로도 등록하지 않을 경로. */
    private static final String UNREGISTERED_PUBLIC = "/api/performances/1/no-such-path";
    private static final long USER_ID = 42L;

    @Autowired
    private JwtEncoder jwtEncoder;

    static Stream<Arguments> protectedFeatureEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/holds"),
                Arguments.of(HttpMethod.GET, "/api/holds/1"),
                Arguments.of(HttpMethod.DELETE, "/api/holds/1"),
                Arguments.of(HttpMethod.POST, "/api/payments"),
                Arguments.of(HttpMethod.GET, "/api/reservations"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedFeatureEndpoints")
    @DisplayName("R6: 토큰 없이 선점·선점 조회·해제·결제·내 예매를 부르면 401 AUTH_REQUIRED다")
    void featureEndpointsRequireToken(HttpMethod method, String path) throws Exception {
        expectAuthRequired(mockMvc.perform(request(method, path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")));
    }

    static Stream<Arguments> invalidTokens() {
        Instant now = MutableClock.DEFAULT_INSTANT;
        Instant exp = now.plus(Duration.ofHours(1));
        return Stream.of(
                Arguments.of("다른 키로 서명", (TokenFactory) encoder -> TestTokens.signWithOtherKey(USER_ID, now, exp)),
                Arguments.of("서명 변조", (TokenFactory) encoder -> tamperSignature(TestTokens.sign(encoder, USER_ID, now, exp))),
                Arguments.of("내용 변조(sub 변경, 서명 그대로)",
                        (TokenFactory) encoder -> tamperPayload(TestTokens.sign(encoder, USER_ID, now, exp))),
                Arguments.of("서명 없음(alg none)", (TokenFactory) encoder -> TestTokens.unsigned(USER_ID, exp)),
                Arguments.of("JWT 형식이 아님", (TokenFactory) encoder -> "not-a-jwt"),
                Arguments.of("세 부분이지만 해석할 수 없음", (TokenFactory) encoder -> "aaa.bbb.ccc"),
                Arguments.of("exp 없음", (TokenFactory) encoder -> TestTokens.sign(encoder,
                        c -> c.subject(String.valueOf(USER_ID)).issuedAt(now))),
                Arguments.of("sub 없음", (TokenFactory) encoder -> TestTokens.sign(encoder,
                        c -> c.issuedAt(now).expiresAt(exp))),
                Arguments.of("sub가 사용자 id(숫자)가 아님", (TokenFactory) encoder -> TestTokens.sign(encoder,
                        c -> c.subject("alice").issuedAt(now).expiresAt(exp))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("R6: 서명이 다르거나 형식이 잘못된 토큰은 401 AUTH_REQUIRED다")
    void invalidTokenIsRejected(String description, TokenFactory factory) throws Exception {
        String token = factory.create(jwtEncoder);

        expectAuthRequired(mockMvc.perform(get(UNREGISTERED_PRIVATE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    @DisplayName("R6: Bearer 뒤가 비었거나 Bearer가 아닌 인증 헤더는 토큰이 없는 것과 같이 401 AUTH_REQUIRED다")
    void emptyOrNonBearerHeader() throws Exception {
        expectAuthRequired(mockMvc.perform(get(UNREGISTERED_PRIVATE).header(HttpHeaders.AUTHORIZATION, "Bearer ")));
        expectAuthRequired(mockMvc.perform(get(UNREGISTERED_PRIVATE).header(HttpHeaders.AUTHORIZATION, "Basic YTpi")));
    }

    @Test
    @DisplayName("R6: 같은 키로 서명한 정상 토큰은 인증을 통과한다(등록되지 않은 비공개 경로가 404)")
    void validTokenPasses() throws Exception {
        String token = TestTokens.sign(jwtEncoder, USER_ID, clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        expectProblem(mockMvc.perform(get(UNREGISTERED_PRIVATE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
                404, "NOT_FOUND");
    }

    @Test
    @DisplayName("R6: exp 1초 전과 exp와 같은 시각의 토큰은 허용하고, exp 1초 뒤 토큰은 401 AUTH_REQUIRED다")
    void expiryBoundary() throws Exception {
        Instant issuedAt = clock.instant();
        Instant exp = issuedAt.plus(Duration.ofHours(1));
        String token = TestTokens.sign(jwtEncoder, USER_ID, issuedAt, exp);

        clock.setInstant(exp.minusSeconds(1));
        expectProblem(withToken(get(UNREGISTERED_PRIVATE), token), 404, "NOT_FOUND");

        clock.setInstant(exp);
        expectProblem(withToken(get(UNREGISTERED_PRIVATE), token), 404, "NOT_FOUND");

        clock.setInstant(exp.plusSeconds(1));
        expectAuthRequired(withToken(get(UNREGISTERED_PRIVATE), token));
    }

    @Test
    @DisplayName("R6: 로그인 API로 받은 토큰은 인증을 통과하고(등록되지 않은 비공개 경로가 404), 토큰이 없으면 401이다")
    void loginTokenPassesAuthentication() throws Exception {
        testUsers.create("alice", "alice-password");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"alice-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        expectAuthRequired(mockMvc.perform(get(UNREGISTERED_PRIVATE)));
        expectProblem(withToken(get(UNREGISTERED_PRIVATE), token), 404, "NOT_FOUND");
        // 로그인 토큰도 1시간 뒤 1초가 지나면 만료다.
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        expectAuthRequired(withToken(get(UNREGISTERED_PRIVATE), token));
    }

    @Test
    @DisplayName("인증 없이 열리는 경로(POST 로그인, GET 공연, GET health)는 토큰 없이 인증 검사를 지난다")
    void publicPathsWithoutToken() throws Exception {
        expectProblem(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")),
                400, "INVALID_INPUT");
        expectProblem(mockMvc.perform(get(UNREGISTERED_PUBLIC)), 404, "NOT_FOUND");
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 경로라도 정해진 메서드가 아니면 인증이 필요하다(토큰 없이 401 AUTH_REQUIRED)")
    void publicPathsAreMethodSpecific() throws Exception {
        expectAuthRequired(mockMvc.perform(get("/api/auth/login")));
        expectAuthRequired(mockMvc.perform(post(UNREGISTERED_PUBLIC)));
        expectAuthRequired(mockMvc.perform(post("/actuator/health")));
        expectAuthRequired(mockMvc.perform(get("/actuator/info")));
    }

    @Test
    @DisplayName("공개 API에 잘못된 토큰을 붙이면 401 AUTH_REQUIRED이고, 토큰 검사를 지난 공개 경로에서도 지원하지 않는 메서드는 400 INVALID_INPUT, 없는 경로는 404 NOT_FOUND다")
    void publicPathsWithToken() throws Exception {
        String expired = TestTokens.sign(jwtEncoder, USER_ID, clock.instant().minus(Duration.ofHours(2)),
                clock.instant().minus(Duration.ofHours(1)));
        String wrongKey = TestTokens.signWithOtherKey(USER_ID, clock.instant(), clock.instant().plus(Duration.ofHours(1)));
        String valid = TestTokens.sign(jwtEncoder, USER_ID, clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        for (String bad : new String[] {expired, wrongKey, "not-a-jwt"}) {
            expectAuthRequired(withToken(get(UNREGISTERED_PUBLIC), bad));
            expectAuthRequired(withToken(get("/actuator/health"), bad));
            expectAuthRequired(withToken(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"), bad));
        }

        expectProblem(withToken(get("/api/auth/login"), valid), 400, "INVALID_INPUT");
        expectProblem(withToken(get(UNREGISTERED_PUBLIC), valid), 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("e2e가 아닌 프로필에서 POST /api/test-support/reset은 토큰 없이 401 AUTH_REQUIRED, 유효한 토큰이면 404 NOT_FOUND다")
    void resetPathOutsideE2eProfile() throws Exception {
        String valid = TestTokens.sign(jwtEncoder, USER_ID, clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        expectAuthRequired(mockMvc.perform(post("/api/test-support/reset")));
        // CSRF 보호를 껐으므로 토큰만 있으면 POST도 403이 아니다.
        expectProblem(withToken(post("/api/test-support/reset"), valid), 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("인증된 요청에도 세션과 쿠키를 만들지 않는다(STATELESS)")
    void noSessionIsCreated() throws Exception {
        String valid = TestTokens.sign(jwtEncoder, USER_ID, clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        MvcResult authenticated = withToken(get(UNREGISTERED_PRIVATE), valid).andReturn();
        MvcResult rejected = mockMvc.perform(get(UNREGISTERED_PRIVATE)).andReturn();

        for (MvcResult result : new MvcResult[] {authenticated, rejected}) {
            assertThat(result.getRequest().getSession(false)).isNull();
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    private ResultActions withToken(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static void expectAuthRequired(ResultActions result) throws Exception {
        expectProblem(result, 401, "AUTH_REQUIRED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private static String tamperSignature(String token) {
        int dot = token.lastIndexOf('.');
        char last = token.charAt(token.length() - 1);
        // 마지막 글자는 패딩 비트가 있어 바꿔도 같은 바이트일 수 있으므로 서명의 첫 글자를 바꾼다.
        char first = token.charAt(dot + 1);
        return token.substring(0, dot + 1) + (first == 'A' ? 'B' : 'A') + token.substring(dot + 2, token.length() - 1) + last;
    }

    private static String tamperPayload(String token) {
        String[] parts = token.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"" + USER_ID + "\"", "\"sub\":\"1\"");
        return parts[0] + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
    }

    @FunctionalInterface
    interface TokenFactory {
        String create(JwtEncoder encoder);
    }
}
