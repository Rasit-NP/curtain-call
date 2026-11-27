package com.curtaincall.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.curtaincall.testsupport.api.TestDataResetController;
import com.curtaincall.testsupport.application.TestDataResetService;

/**
 * E2E 데이터 초기화의 등록 조건을 DB 없이 확인한다(spec 6.9). 두 클래스만 등록하는 작은 컨텍스트를 프로필마다 띄운다.
 *
 * <p>local은 사람이 개발할 때 쓰는 DB에 붙으므로, 이 프로필에서 초기화가 열리면 개발 데이터가 지워진다. 그래서 통합 테스트가 띄우지 않는
 * local·prod와 프로필이 없는 경우도 본다. e2e에서 등록되는 것은 실제 컨텍스트로 TestDataResetApiTest가 확인한다.
 */
class TestDataResetProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestDataResetController.class, TestDataResetService.class);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"local", "test", "prod"})
    @DisplayName("e2e가 아닌 프로필에서는 초기화 컨트롤러와 서비스를 등록하지 않는다")
    void notRegisteredOutsideE2eProfile(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TestDataResetController.class);
            assertThat(context).doesNotHaveBean(TestDataResetService.class);
        });
    }

    @Test
    @DisplayName("프로필을 주지 않아도 초기화 컨트롤러와 서비스를 등록하지 않는다")
    void notRegisteredWithoutProfile() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TestDataResetController.class);
            assertThat(context).doesNotHaveBean(TestDataResetService.class);
        });
    }
}
