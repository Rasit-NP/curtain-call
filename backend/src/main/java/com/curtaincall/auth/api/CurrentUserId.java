package com.curtaincall.auth.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러가 현재 사용자 id를 얻는 유일한 방법. 액세스 토큰(Jwt)의 sub를 long으로 바꿔 넣는다.
 *
 * <pre>{@code
 * @PostMapping("/api/holds")
 * HoldResponse create(@CurrentUserId long userId, @Valid @RequestBody CreateHoldRequest request) { ... }
 * }</pre>
 *
 * <p>sub가 1 이상의 long인 토큰만 인증을 통과한다(JwtConfig의 검증기). 공개 경로에서 쓰면 토큰 없는 요청은
 * 401 AUTH_REQUIRED다({@link CurrentUserIds}).
 * 테스트에서는 {@code AuthRequests.asUser(id)}로 요청을 만든다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(com.curtaincall.auth.api.CurrentUserIds).from(#this)", errorOnInvalidType = true)
public @interface CurrentUserId {
}
