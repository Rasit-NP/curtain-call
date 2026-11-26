package com.curtaincall.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 코드 없이 상태만 정해진 오류의 code(결정 0012, 0037). */
class ErrorCodeStatusMappingTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "400, INVALID_INPUT", "405, INVALID_INPUT", "406, INVALID_INPUT", "413, INVALID_INPUT", "415, INVALID_INPUT",
            "404, NOT_FOUND",
            "409, INTERNAL_ERROR", "403, INTERNAL_ERROR", "501, INTERNAL_ERROR", "500, INTERNAL_ERROR",
    })
    @DisplayName("앱 안(MVC 예외, /error)의 상태: 입력 오류 상태만 INVALID_INPUT, 404는 NOT_FOUND, 그 밖은 INTERNAL_ERROR(결정 0012 그대로)")
    void frameworkStatus(int status, ErrorCode expected) {
        assertThat(ErrorCode.forFrameworkStatus(status)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "400, INVALID_INPUT", "408, INVALID_INPUT", "413, INVALID_INPUT", "417, INVALID_INPUT", "431, INVALID_INPUT",
            "501, INVALID_INPUT", "505, INVALID_INPUT",
            "404, NOT_FOUND",
            "500, INTERNAL_ERROR", "503, INTERNAL_ERROR",
    })
    @DisplayName("Tomcat이 앱에 넘기기 전에 거부한 요청의 상태: 4xx와 501·505(요청을 해석할 수 없음)는 INVALID_INPUT, 404는 NOT_FOUND, 그 밖의 5xx는 INTERNAL_ERROR")
    void containerStatus(int status, ErrorCode expected) {
        assertThat(ErrorCode.forContainerStatus(status)).isEqualTo(expected);
    }
}
