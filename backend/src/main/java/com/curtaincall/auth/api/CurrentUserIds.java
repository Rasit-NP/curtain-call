package com.curtaincall.auth.api;

import org.springframework.security.oauth2.jwt.Jwt;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;

/**
 * {@link CurrentUserId}가 인증 주체에서 사용자 id를 꺼낼 때 쓴다. 컨트롤러가 직접 부르지 않는다.
 *
 * <p>액세스 토큰(Jwt)이 아니면(공개 경로의 익명 요청) 401 AUTH_REQUIRED다. 사용자 id가 필요한 처리는 로그인이 필요하기 때문이다(R6).
 * sub는 토큰 검증에서 이미 1 이상의 long인지 확인하지만, 변환에 실패해도 500이 아니라 같은 401로 둔다.
 */
public final class CurrentUserIds {

    private CurrentUserIds() {
    }

    public static long from(Object principal) {
        if (principal instanceof Jwt jwt && jwt.getSubject() != null) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException e) {
                throw new ApiException(ErrorCode.AUTH_REQUIRED);
            }
        }
        throw new ApiException(ErrorCode.AUTH_REQUIRED);
    }
}
