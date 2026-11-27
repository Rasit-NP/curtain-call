package com.curtaincall.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 멱등 기록(R5, spec 6.7.1). 기록은 DB INSERT로 만들므로, 여기서는 같은 패키지의 기본 생성자로 빈 기록을 만든다. */
class IdempotencyRecordTest {

    @Test
    @DisplayName("R5: 멱등 기록의 응답은 한 번만 채운다. 다시 채우려 하면 IllegalStateException이고 처음 응답이 남는다")
    void responseIsCompletedOnce() {
        IdempotencyRecord record = new IdempotencyRecord();
        StoredResponse first = new StoredResponse(201, "{\"paymentId\":1}");
        record.complete(first);

        assertThatThrownBy(() -> record.complete(new StoredResponse(402, "{\"code\":\"PAYMENT_FAILED\"}")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(record.response()).isEqualTo(first);
    }
}
