package com.curtaincall.testsupport.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.testsupport.application.TestDataResetService;

/**
 * E2E 데이터 초기화 {@code POST /api/test-support/reset}(spec 6.9). 데이터를 비우고 시드를 다시 넣은 뒤 {@code 204 No Content}.
 *
 * <p>e2e 프로필에서만 등록하고, 보안 설정도 그 프로필에서만 이 경로를 토큰 없이 연다(SecurityConfig). 다른 프로필에서는 등록하지 않으므로
 * 다른 경로처럼 토큰 없이 401 AUTH_REQUIRED, 유효한 토큰이면 404 NOT_FOUND다. local은 개발 DB를 쓰므로 여기서 열리면 개발 데이터가 지워진다.
 *
 * <p>E2E는 테스트마다 먼저 이것을 부른다(e2e/CLAUDE.md). 초기화 뒤의 id가 같다고 가정하지 않는다.
 */
@Profile("e2e")
@RestController
@RequestMapping("/api/test-support")
public class TestDataResetController {

    private final TestDataResetService resetService;

    public TestDataResetController(TestDataResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        resetService.reset();
    }
}
