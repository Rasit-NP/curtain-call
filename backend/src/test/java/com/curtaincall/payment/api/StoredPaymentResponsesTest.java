package com.curtaincall.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.payment.domain.StoredResponse;

import tools.jackson.databind.json.JsonMapper;

/** 멱등 기록에 저장한 응답으로 결제 응답을 다시 만드는 변환(R5, 결정 0150). 스프링 컨텍스트 없이 만든다. */
class StoredPaymentResponsesTest {

    private static final String PAYMENT_FAILED_BODY = "{\"code\":\"PAYMENT_FAILED\",\"detail\":\"결제가 승인되지 않았습니다.\"}";

    private final StoredPaymentResponses responses = new StoredPaymentResponses(JsonMapper.builder().build());

    @Test
    @DisplayName("R5: 저장한 응답의 상태 코드와 code가 맞지 않으면 다른 응답을 만들지 않고 IllegalStateException을 던진다")
    void storedStatusMustMatchCode() {
        assertThatThrownBy(() -> responses.respond(new StoredResponse(402, PAYMENT_FAILED_BODY)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED));

        assertThatThrownBy(() -> responses.respond(new StoredResponse(409, PAYMENT_FAILED_BODY)))
                .isInstanceOf(IllegalStateException.class);
    }
}
