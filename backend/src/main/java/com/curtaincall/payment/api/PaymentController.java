package com.curtaincall.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.auth.api.CurrentUserId;
import com.curtaincall.payment.application.PaymentService;
import com.curtaincall.payment.domain.StoredResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** 결제(spec 6.7). 로그인이 필요한 경로다(보안 설정은 SecurityConfig, R6). */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final StoredPaymentResponses storedResponses;

    public PaymentController(PaymentService paymentService, StoredPaymentResponses storedResponses) {
        this.paymentService = paymentService;
        this.storedResponses = storedResponses;
    }

    /**
     * spec 6.7 결제. 오류 순서: 401 AUTH_REQUIRED(보안 설정) → 400 INVALID_INPUT(본문 검증, Idempotency-Key 헤더 검증)
     * → 3번 멱등 키(저장한 응답, 같은 키·다른 선점은 400) → 404 NOT_FOUND → 409 HOLD_EXPIRED → 402 PAYMENT_FAILED(서비스의 결과).
     *
     * <p>서비스는 결과(거부·거절 포함)를 저장할 응답으로 바꿔 멱등 기록에 저장하고 커밋한다(R5, spec 6.7.1). 응답은 커밋한 뒤 그 저장한 응답으로 만든다.
     * 처음 요청과 같은 키로 다시 보낸 요청이 같은 경로를 지나므로 응답이 같다. 오류 응답은 공통 처리기가 만든다.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@CurrentUserId long userId, @Valid @RequestBody PayRequest request, HttpServletRequest servletRequest) {
        String idempotencyKey = IdempotencyKeyHeader.read(servletRequest);
        StoredResponse stored = paymentService.pay(userId, request.holdId(), idempotencyKey, storedResponses::toStored);
        return storedResponses.respond(stored);
    }
}
