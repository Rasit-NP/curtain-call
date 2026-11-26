package com.curtaincall.common.api;

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
}
