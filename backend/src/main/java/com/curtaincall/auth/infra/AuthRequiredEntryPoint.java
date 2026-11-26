package com.curtaincall.auth.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 토큰이 없거나(서명·형식이 잘못됐거나 만료됐거나) 인증이 필요한 경로에 익명으로 오면 401 AUTH_REQUIRED(R6, spec 3.1).
 *
 * <p>응답 본문은 공통 처리기(GlobalExceptionHandler)가 만들도록 MVC의 예외 처리기에 넘긴다. 오류 형식을 한곳에서 만들기 위해서다.
 */
@Component
public class AuthRequiredEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(AuthRequiredEntryPoint.class);

    private final HandlerExceptionResolver exceptionResolver;

    public AuthRequiredEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        log.debug("인증 실패: {}", authException.getMessage());
        // RFC 6750: 토큰이 있었지만 잘못됐으면 invalid_token을 알린다. 원인 설명은 응답에 넣지 않는다.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                authException instanceof OAuth2AuthenticationException ? "Bearer error=\"invalid_token\"" : "Bearer");
        exceptionResolver.resolveException(request, response, null, new ApiException(ErrorCode.AUTH_REQUIRED));
    }
}
