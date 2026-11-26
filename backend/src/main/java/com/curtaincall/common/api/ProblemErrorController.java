package com.curtaincall.common.api;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Spring MVC 밖(서블릿 필터, 서블릿 컨테이너)에서 난 오류의 응답(spec 3.1, PR #8에서 넘겨받은 일).
 *
 * <p>그런 오류는 컨테이너가 /error로 전달한다. Boot 기본 BasicErrorController 대신 이 컨트롤러가 받아, 원래 상태로
 * code를 정한 {@link ApiException}을 던진다. 응답 본문은 다른 오류와 같이 GlobalExceptionHandler가 만든다.
 * 상태 → code는 MVC 예외와 같은 규칙({@link ErrorCode#forFrameworkStatus(int)})이다. 예: Tomcat의 잘못된 퍼센트 인코딩(400)
 * → 400 INVALID_INPUT, 필터에서 던진 예외(500) → 500 INTERNAL_ERROR(원인은 로그에만).
 *
 * <p>/error로의 오류 전달(ERROR 디스패치)은 보안 설정에서 인증 없이 지나게 했다. /error를 직접 부르는 요청은 다른 비공개
 * 경로처럼 인증이 필요하고, 토큰이 있으면 오류 정보가 없으므로 404 NOT_FOUND다.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public void error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (!(status instanceof Integer code)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Throwable cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable t ? t : null;
        throw new ApiException(ErrorCode.forFrameworkStatus(code), cause);
    }
}
