package com.curtaincall.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.auth.api.CurrentUserId;
import com.curtaincall.common.api.ApiException;
import com.curtaincall.payment.application.PaymentOutcome;
import com.curtaincall.payment.application.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** 결제(spec 6.7). 로그인이 필요한 경로다(보안 설정은 SecurityConfig, R6). */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * spec 6.7 결제. 오류 순서: 401 AUTH_REQUIRED(보안 설정) → 400 INVALID_INPUT(본문 검증, Idempotency-Key 헤더 검증)
     * → (3번 멱등 키는 #56) → 404 NOT_FOUND → 409 HOLD_EXPIRED → 402 PAYMENT_FAILED(서비스의 결과).
     *
     * <p>서비스는 거부·거절도 결과로 돌려주고 트랜잭션을 커밋한다. 오류 응답은 커밋한 뒤 여기서 공통 처리기로 넘긴다.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@CurrentUserId long userId, @Valid @RequestBody PayRequest request, HttpServletRequest servletRequest) {
        // 키를 저장해 같은 키의 결과를 돌려주는 것은 #56에서 한다. 여기서는 형식만 검증한다.
        IdempotencyKeyHeader.read(servletRequest);
        PaymentOutcome outcome = paymentService.pay(userId, request.holdId());
        return switch (outcome) {
            case PaymentOutcome.Approved approved -> PaymentResponse.from(approved);
            case PaymentOutcome.Rejected rejected -> throw new ApiException(rejected.code(), rejected.detail());
        };
    }
}
