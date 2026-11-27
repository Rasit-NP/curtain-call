package com.curtaincall.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.SeedFiles;

/**
 * e2e 프로필의 보안 설정과 계정 시드(spec 2.5, 6.9).
 *
 * <p>test 프로필에 e2e를 더해 띄운다(DB는 Testcontainers, Flyway 위치는 test 문서가 덮어써 시드를 넣지 않는다).
 * 테스트마다 테이블을 비운 뒤 {@code db/seed}의 시드 파일을 직접 실행한다. reset 컨트롤러가 할 일과 같다.
 */
@ActiveProfiles("e2e")
class E2eProfileAuthTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @BeforeEach
    void applySeed() throws IOException {
        SeedFiles.apply(dataSource);
    }

    @Test
    @DisplayName("e2e 프로필이 켜져 있다")
    void e2eProfileIsActive() {
        assertThat(environment.getActiveProfiles()).contains("test", "e2e");
    }

    @Test
    @DisplayName("e2e 프로필에서는 POST /api/test-support/reset을 토큰 없이 부를 수 있다(인증 검사를 지나 초기화 컨트롤러가 204)")
    void resetPathIsOpenInE2eProfile() throws Exception {
        mockMvc.perform(post("/api/test-support/reset"))
                .andExpect(status().isNoContent());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "demo, demo1234",
            "e2e-user1, e2e-pass1",
            "e2e-user2, e2e-pass2",
    })
    @DisplayName("시드 계정(시연용 1개, E2E용 2개)으로 로그인할 수 있다")
    void seedAccountsCanLogIn(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }
}
