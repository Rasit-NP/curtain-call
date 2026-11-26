package com.curtaincall.common.api;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 * <ul>
 *   <li>{@link ApiException}: 그 코드의 상태와 code, 설명</li>
 *   <li>Spring MVC의 입력 오류(잘못된 JSON, 형식이 틀린 경로·쿼리 값, 검증 실패, 405, 406, 413, 415) → 400 INVALID_INPUT.
 *       405·415 등도 표에 있는 코드만 쓰기 위해 400으로 바꾼다.</li>
 *   <li>클래스에 @Validated를 붙인 빈의 메서드 검증 실패(ConstraintViolationException) → 400 INVALID_INPUT</li>
 *   <li>없는 경로(404) → 404 NOT_FOUND</li>
 *   <li>그 밖의 모든 예외(코드를 정하지 않은 ResponseStatusException 포함) → 500 INTERNAL_ERROR. 원인은 로그에만 남긴다.</li>
 * </ul>
 *
 * <p>응답은 모두 부모의 handleExceptionInternal을 거친다. 이미 커밋된 응답은 건드리지 않고, 500이면 요청에 예외를 남겨
 * 관측(메트릭의 exception 태그)에 잡히게 하는 Spring의 처리를 그대로 쓰기 위해서다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** INVALID_INPUT으로 바꾸는 Spring MVC 예외의 상태. */
    private static final Set<Integer> INPUT_ERROR_STATUSES = Set.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            HttpStatus.NOT_ACCEPTABLE.value(),
            HttpStatus.CONTENT_TOO_LARGE.value(),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());

    @ExceptionHandler(ApiException.class)
    public @Nullable ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        return respond(ex, ex.errorCode(), ex.getMessage(), new HttpHeaders(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public @Nullable ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        return respond(ex, ErrorCode.INVALID_INPUT, null, new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return respond(ex, ErrorCode.INTERNAL_ERROR, null, new HttpHeaders(), request);
    }

    /** ResponseEntityExceptionHandler가 다루는 Spring MVC 예외. 상태로 code를 정한다. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return respond(ex, toErrorCode(statusCode), null, headers, request);
    }

    private static ErrorCode toErrorCode(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        if (INPUT_ERROR_STATUSES.contains(statusCode.value())) {
            return ErrorCode.INVALID_INPUT;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private @Nullable ResponseEntity<Object> respond(
            Exception ex, ErrorCode code, @Nullable String detail, HttpHeaders headers, WebRequest request) {
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("처리하지 못한 예외", ex);
        } else {
            log.debug("요청 오류 {}: {}", code, ex.getMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail != null ? detail : code.defaultDetail());
        problem.setProperty("code", code.name());
        return super.handleExceptionInternal(ex, problem, headers, code.status(), request);
    }
}
