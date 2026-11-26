package com.curtaincall.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 인증이 필요한 API 테스트의 요청 도우미. 컨트롤러는 현재 사용자 id를 {@code @CurrentUserId}(액세스 토큰의 sub)로 얻는다.
 *
 * <p>{@code spring-security-test}의 {@code jwt()}는 토큰 해석(서명·만료 검증)을 건너뛰고 인증된 요청을 만든다.
 * 기능 API 테스트는 이것을 쓰고, 토큰 검증 자체는 실제로 서명한 토큰으로 검증한다({@link TestTokens}).
 *
 * <pre>{@code
 * mockMvc.perform(post("/api/holds").with(AuthRequests.asUser(userId)) ...)
 * }</pre>
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** sub에 사용자 id를 담은 인증된 요청. */
    public static RequestPostProcessor asUser(long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }
}
