package com.curtaincall.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.testsupport.api.TestDataResetController;
import com.curtaincall.testsupport.application.TestDataResetService;

/**
 * E2E 데이터 초기화는 e2e 프로필에서만 등록한다(spec 6.9, backend/CLAUDE.md 'E2E 지원'). 통합 테스트의 test 프로필 컨텍스트로 확인한다.
 * 이 프로필에서 경로를 부르면 토큰 없이 401, 유효한 토큰이면 404인 것은 AuthenticationTest가 확인한다.
 * local·prod는 DB 없이 조건만 보는 {@link TestDataResetProfileTest}가 확인한다.
 */
class TestDataResetRegistrationTest extends IntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("e2e가 아닌 프로필(test)에서는 초기화 컨트롤러와 서비스를 등록하지 않는다")
    void notRegisteredOutsideE2eProfile() {
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("test");
        assertThat(context.getBeanNamesForType(TestDataResetController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TestDataResetService.class)).isEmpty();
    }
}
