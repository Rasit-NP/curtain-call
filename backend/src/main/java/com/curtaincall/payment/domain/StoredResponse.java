package com.curtaincall.payment.domain;

import java.util.Objects;

/**
 * 멱등 기록에 저장한 결제 응답(spec 6.7.1): 상태 코드(201, 402, 404, 409)와 본문. 본문의 모양은 API 계층이 정한다(결정 0150).
 * 같은 사용자가 같은 키·같은 선점으로 다시 보내면 이 값으로 처음과 같은 응답을 만든다.
 */
public record StoredResponse(int status, String body) {

    public StoredResponse {
        Objects.requireNonNull(body);
    }
}
