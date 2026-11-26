package com.curtaincall.common.api;

import java.util.Set;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. 루트 CLAUDE.md의 'API 오류 코드' 표와 같아야 한다(ErrorCodeTest가 확인).
 */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제가 승인되지 않았습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점되거나 판매된 좌석입니다."),
    HOLD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 공연에서 최대 4석까지 가질 수 있습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "선점 시간이 만료되었습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    /** 프레임워크·컨테이너가 정한 상태 중 INVALID_INPUT으로 바꾸는 것(spec 3.1: 표에 있는 코드만 쓴다). */
    private static final Set<Integer> INPUT_ERROR_STATUSES = Set.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            HttpStatus.NOT_ACCEPTABLE.value(),
            HttpStatus.CONTENT_TOO_LARGE.value(),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());

    private final HttpStatus status;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String defaultDetail) {
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }

    /**
     * 코드 없이 상태만 정해진 오류(Spring MVC 예외, 서블릿 컨테이너의 오류)의 code.
     * 404는 NOT_FOUND, 입력 오류 상태(400·405·406·413·415)는 INVALID_INPUT, 그 밖은 INTERNAL_ERROR(결정 0012).
     */
    public static ErrorCode forFrameworkStatus(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return NOT_FOUND;
        }
        if (INPUT_ERROR_STATUSES.contains(status)) {
            return INVALID_INPUT;
        }
        return INTERNAL_ERROR;
    }
}
