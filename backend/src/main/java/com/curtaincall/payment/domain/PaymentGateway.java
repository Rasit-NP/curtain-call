package com.curtaincall.payment.domain;

/**
 * 결제 승인을 맡는 외부 연동(spec 6.7.2). 실제 PG는 부르지 않고, 구현은 모의 결제({@code payment.infra.FakePaymentGateway}) 하나다.
 *
 * <p>결제 서비스는 사용자 행·선점 행·좌석을 잠그고 만료 판정을 통과한 뒤 이것을 부른다(spec 2.4, 결정 0100). 응답이 늦어도 잠금을 쥔 채
 * 기다렸다가 결과대로 결제를 마친다.
 */
public interface PaymentGateway {

    /** 결제를 요청하고 승인·거절 결과를 돌려준다. */
    Result pay(Request request);

    /**
     * @param amount 결제 금액(spec 2.2: 공연 가격 × 선점 좌석 수)
     */
    record Request(long userId, long holdId, long amount) {
    }

    enum Result {
        APPROVED,
        DECLINED
    }
}
