package com.curtaincall.common.api;

import org.apache.tomcat.util.http.InvalidParameterException;
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
 *   <li>Tomcat이 쿼리·폼 값을 해석하지 못함(InvalidParameterException, 예: ?x=%zz) → Tomcat이 정한 상태로(400 INVALID_INPUT)</li>
 *   <li>없는 경로(404) → 404 NOT_FOUND</li>
 *   <li>그 밖의 모든 예외(코드를 정하지 않은 ResponseStatusException 포함) → 500 INTERNAL_ERROR. 원인은 로그에만 남긴다.</li>
 * </ul>
 *
 * <p>MVC 밖의 오류도 여기서 형식을 만든다. 인증 실패는 AuthRequiredEntryPoint가, 서블릿 필터·컨테이너 단계의 오류는
 * ProblemErrorController(/error)가 {@link ApiException}으로 넘긴다. 요청이 앱에 닿기 전에 Tomcat이 거부한 경우(잘못된 경로
 * 인코딩 등)만 ProblemDetailErrorReportValve가 같은 형식으로 직접 쓴다.
 *
 * <p>응답은 모두 부모의 handleExceptionInternal을 거친다. 이미 커밋된 응답은 건드리지 않고, 500이면 요청에 예외를 남겨
 * 관측(메트릭의 exception 태그)에 잡히게 하는 Spring의 처리를 그대로 쓰기 위해서다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public @Nullable ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        return respond(ex, ex.errorCode(), ex.getMessage(), new HttpHeaders(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public @Nullable ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        return respond(ex, ErrorCode.INVALID_INPUT, null, new HttpHeaders(), request);
    }

    /**
     * Tomcat은 쿼리·폼 값을 처음 읽을 때 해석한다. 퍼센트 인코딩이 잘못됐거나 값이 너무 많으면 그때 이 예외를 던지므로
     * 컨트롤러 인자를 만들다가 여기로 온다. Tomcat이 정한 상태(400, 413 등)로 code를 정한다(입력 오류 → 400 INVALID_INPUT).
     */
    @ExceptionHandler(InvalidParameterException.class)
    public @Nullable ResponseEntity<Object> handleInvalidParameter(InvalidParameterException ex, WebRequest request) {
        int status = ex.getErrorCode() > 0 ? ex.getErrorCode() : HttpStatus.BAD_REQUEST.value();
        return respond(ex, ErrorCode.forFrameworkStatus(status), null, new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return respond(ex, ErrorCode.INTERNAL_ERROR, null, new HttpHeaders(), request);
    }

    /** ResponseEntityExceptionHandler가 다루는 Spring MVC 예외. 상태로 code를 정한다. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return respond(ex, ErrorCode.forFrameworkStatus(statusCode.value()), null, headers, request);
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
