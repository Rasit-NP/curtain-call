package com.curtaincall.common.api;

import org.jspecify.annotations.Nullable;

/**
 * 기능 코드가 오류 코드 표의 오류를 알릴 때 던진다. 공통 처리기가 code의 상태와 code로 응답한다.
 * detail을 주지 않으면 {@link ErrorCode#defaultDetail()}을 쓴다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultDetail());
    }

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    /** 원인 예외를 함께 남긴다. 응답에는 넣지 않고, 500이면 로그에 원인까지 남는다. */
    public ApiException(ErrorCode errorCode, @Nullable Throwable cause) {
        super(errorCode.defaultDetail(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
