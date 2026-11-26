package com.curtaincall.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

import com.curtaincall.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** spec 6.1 로그인. 오류 표의 순서: 400 INVALID_INPUT → 401 INVALID_CREDENTIALS. */
class LoginApiTest extends IntegrationTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private JwtDecoder jwtDecoder;

    private long userId;

    @BeforeEach
    void createUser() {
        userId = testUsers.create("alice", PASSWORD);
    }

    @Test
    @DisplayName("로그인에 성공하면 200과 accessToken, expiresIn 3600을 준다")
    void loginSucceeds() throws Exception {
        String body = login("{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

        // spec 3.2: sub는 사용자 id(문자열), exp는 발급 시각(주입한 시계) + 1시간.
        Jwt token = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(token.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(token.getIssuedAt()).isEqualTo(clock.instant());
        assertThat(token.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(1)));
        assertThat(token.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    @DisplayName("없는 아이디와 틀린 비밀번호는 구분하지 않고 모두 401 INVALID_CREDENTIALS다")
    void wrongCredentials() throws Exception {
        String unknownUser = expectProblem(login("{\"username\":\"nobody\",\"password\":\"" + PASSWORD + "\"}"),
                401, "INVALID_CREDENTIALS");
        String wrongPassword = expectProblem(login("{\"username\":\"alice\",\"password\":\"wrong-password\"}"),
                401, "INVALID_CREDENTIALS");

        assertThat(JsonPath.<String>read(unknownUser, "$.detail")).isEqualTo(JsonPath.read(wrongPassword, "$.detail"));
    }

    @Test
    @DisplayName("아이디 대소문자나 앞뒤 공백이 다르면 다른 아이디로 보고 401 INVALID_CREDENTIALS다")
    void usernameIsExact() throws Exception {
        expectProblem(login("{\"username\":\"Alice\",\"password\":\"" + PASSWORD + "\"}"), 401, "INVALID_CREDENTIALS");
        expectProblem(login("{\"username\":\" alice\",\"password\":\"" + PASSWORD + "\"}"), 401, "INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("비밀번호가 BCrypt 한도(72바이트)를 넘어도 500이 아니라 401 INVALID_CREDENTIALS다")
    void overlongPasswordIsInvalidCredentials() throws Exception {
        String longPassword = PASSWORD + "x".repeat(100);

        expectProblem(login("{\"username\":\"alice\",\"password\":\"" + longPassword + "\"}"), 401, "INVALID_CREDENTIALS");
        expectProblem(login("{\"username\":\"nobody\",\"password\":\"" + longPassword + "\"}"), 401, "INVALID_CREDENTIALS");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"password\":\"" + PASSWORD + "\"}",
            "{\"username\":\"alice\"}",
            "{\"username\":\"\",\"password\":\"" + PASSWORD + "\"}",
            "{\"username\":\"alice\",\"password\":\"\"}",
            "{\"username\":\"   \",\"password\":\"" + PASSWORD + "\"}",
            "{\"username\":\"alice\",\"password\":\" \\t \"}",
            "{\"username\":null,\"password\":null}",
            "{}",
    })
    @DisplayName("아이디나 비밀번호가 없거나 비었거나 공백뿐이면 400 INVALID_INPUT이다")
    void blankFields(String body) throws Exception {
        expectProblem(login(body), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("본문이 없거나 올바른 JSON이 아니면 400 INVALID_INPUT이다")
    void malformedBody() throws Exception {
        expectProblem(login(""), 400, "INVALID_INPUT");
        expectProblem(login("{\"username\":"), 400, "INVALID_INPUT");
    }

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String expectProblem(ResultActions result, int status, String code) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }
}
