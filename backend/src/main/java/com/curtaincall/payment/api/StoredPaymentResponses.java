package com.curtaincall.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.payment.application.PaymentOutcome;
import com.curtaincall.payment.domain.StoredResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 응답과 멱등 기록에 저장하는 응답(spec 6.7.1: 상태 코드와 본문) 사이의 변환(결정 0150).
 *
 * <ul>
 *   <li>승인(201): 응답 본문 {@link PaymentResponse}를 JSON으로 저장하고, 그 JSON을 읽어 응답한다.</li>
 *   <li>거부·거절(402, 404, 409): 상태와 code·detail을 저장하고, 같은 code·detail로 {@link ApiException}을 던져 공통 처리기가 오류 본문을 만든다.
 *       오류 본문(ProblemDetail)은 공통 처리기 한곳에서만 만든다(backend/CLAUDE.md). type·title·status·instance는 상태와 요청 경로로 정해지므로
 *       code와 detail만 저장하면 같은 본문이 된다.</li>
 * </ul>
 *
 * <p>처음 응답도 저장한 응답으로 만든다({@link #respond}). 같은 키로 다시 보낸 요청과 처음 요청의 응답이 같은 값에서 같은 경로로 만들어지므로 같다.
 */
@Component
public class StoredPaymentResponses {

    private final JsonMapper jsonMapper;

    public StoredPaymentResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 결제 결과를 저장할 응답으로 바꾼다. */
    public StoredResponse toStored(PaymentOutcome outcome) {
        return switch (outcome) {
            case PaymentOutcome.Approved approved ->
                    new StoredResponse(HttpStatus.CREATED.value(), jsonMapper.writeValueAsString(PaymentResponse.from(approved)));
            case PaymentOutcome.Rejected rejected -> new StoredResponse(rejected.code().status().value(),
                    jsonMapper.writeValueAsString(new StoredError(rejected.code(), rejected.detail())));
        };
    }

    /**
     * 저장한 응답으로 응답한다. 201이면 응답 본문을 돌려주고, 아니면 저장한 code·detail의 {@link ApiException}을 던진다.
     *
     * @throws ApiException 저장한 응답이 402, 404, 409일 때
     */
    public PaymentResponse respond(StoredResponse stored) {
        if (stored.status() == HttpStatus.CREATED.value()) {
            return jsonMapper.readValue(stored.body(), PaymentResponse.class);
        }
        StoredError error = jsonMapper.readValue(stored.body(), StoredError.class);
        if (error.code().status().value() != stored.status()) {
            throw new IllegalStateException("저장한 응답의 상태와 code가 맞지 않는다: " + stored.status() + ", " + error.code());
        }
        throw new ApiException(error.code(), error.detail());
    }

    /** 오류 응답을 다시 만들 값. 저장 형식이다(JSON {@code {"code": "...", "detail": "..."}}). */
    record StoredError(ErrorCode code, String detail) {
    }
}
