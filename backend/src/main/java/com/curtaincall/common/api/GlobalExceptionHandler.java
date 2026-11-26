package com.curtaincall.common.api;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

/**
 * 모든 오류 응답을 만드는 한곳(spec 3.1). ProblemDetail에 code를 더하고, code는 {@link ErrorCode}에 있는 것만 쓴다.
 *
 * <p>Spring MVC가 던지는 예외(ResponseEntityExceptionHandler가 다루는 것)는 상태로 code를 정한다.
 * <ul>
 *   <li>404(없는 경로) → 404 NOT_FOUND</li>
 *   <li>5xx → 500 INTERNAL_ERROR</li>
 *   <li>그 밖의 4xx(잘못된 JSON, 형식이 틀린 경로·쿼리 값, 검증 실패, 405, 415 등) → 400 INVALID_INPUT.
 *       405·415도 표에 있는 코드만 쓰기 위해 400으로 바꾼다.</li>
 * </ul>
 * 클래스에 @Validated를 붙인 빈의 메서드 검증 실패(ConstraintViolationException)도 400 INVALID_INPUT이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        log.debug("요청 오류 {}: {}", ErrorCode.INVALID_INPUT, ex.getMessage());
        return problem(ErrorCode.INVALID_INPUT, new HttpHeaders());
    }

    /** 처리하지 못한 예외. 원인은 로그에만 남기고 본문에는 넣지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("처리하지 못한 예외", ex);
        return problem(ErrorCode.INTERNAL_ERROR, new HttpHeaders());
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = toErrorCode(statusCode);
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("처리하지 못한 MVC 예외", ex);
        } else {
            log.debug("요청 오류 {}: {}", code, ex.getMessage());
        }
        return problem(code, headers);
    }

    private static ErrorCode toErrorCode(HttpStatusCode statusCode) {
        if (statusCode.value() == 404) {
            return ErrorCode.NOT_FOUND;
        }
        if (statusCode.is4xxClientError()) {
            return ErrorCode.INVALID_INPUT;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private static ResponseEntity<Object> problem(ErrorCode code, HttpHeaders headers) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.defaultDetail());
        problem.setProperty("code", code.name());
        return ResponseEntity.status(code.status()).headers(headers).body(problem);
    }
}
