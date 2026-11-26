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
 * <p>인증이 필요한 경로에서만 쓴다. sub가 숫자인 토큰만 인증을 통과하므로(JwtConfig의 검증기) 변환은 실패하지 않는다.
 * 테스트에서는 {@code AuthRequests.asUser(id)}로 요청을 만든다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.lang.Long).parseLong(subject)", errorOnInvalidType = true)
public @interface CurrentUserId {
}
