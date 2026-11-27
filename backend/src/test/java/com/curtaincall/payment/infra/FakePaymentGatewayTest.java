package com.curtaincall.payment.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.curtaincall.payment.domain.PaymentGateway.Request;
import com.curtaincall.payment.domain.PaymentGateway.Result;

/**
 * 모의 결제(spec 6.7.2). 테스트가 아닌 프로필에서는 아무것도 지정하지 않으므로 늘 승인하고, 테스트는 다음 호출 거절, 다음 호출 실패, 호출 횟수, 멈추기를 쓴다.
 * 스프링 컨텍스트 없이 만든다.
 */
class FakePaymentGatewayTest {

    private static final Request REQUEST = new Request(7L, 31L, 60000L);

    @Test
    @DisplayName("아무것도 지정하지 않으면 몇 번을 불러도 모두 승인한다(테스트가 아닌 프로필의 동작)")
    void approvesByDefault() {
        FakePaymentGateway gateway = new FakePaymentGateway();

        IntStream.range(0, 5).forEach(i -> assertThat(gateway.pay(REQUEST)).isEqualTo(Result.APPROVED));
    }

    @Test
    @DisplayName("다음 호출을 거절로 지정하면 그 한 번만 거절하고, 그 뒤로는 다시 승인한다")
    void declineNextAppliesToOneCall() {
        FakePaymentGateway gateway = new FakePaymentGateway();

        gateway.declineNext();

        assertThat(gateway.pay(REQUEST)).isEqualTo(Result.DECLINED);
        assertThat(gateway.pay(REQUEST)).isEqualTo(Result.APPROVED);
    }

    @Test
    @DisplayName("호출 횟수를 세고, 초기화하면 호출 횟수와 거절 지정이 지워진다")
    void countsCallsAndResets() {
        FakePaymentGateway gateway = new FakePaymentGateway();
        assertThat(gateway.callCount()).isZero();
        gateway.pay(REQUEST);
        gateway.declineNext();
        gateway.pay(REQUEST);
        assertThat(gateway.callCount()).isEqualTo(2);

        gateway.declineNext();
        gateway.reset();

        assertThat(gateway.callCount()).isZero();
        assertThat(gateway.pay(REQUEST)).isEqualTo(Result.APPROVED);
        assertThat(gateway.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 호출을 멈추게 하면 그 호출은 풀어 줄 때까지 결과를 돌려주지 않고, 멈춘 것을 알 수 있다. 거절 지정과 함께 쓸 수 있다")
    void pauseNextBlocksUntilResumed() throws Exception {
        FakePaymentGateway gateway = new FakePaymentGateway();
        FakePaymentGateway.Pause pause = gateway.pauseNext();
        gateway.declineNext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Result> paused = executor.submit(() -> gateway.pay(REQUEST));

            assertThat(pause.awaitEntered(Duration.ofSeconds(5))).as("멈춘 지점에 들어옴").isTrue();
            assertThat(gateway.callCount()).isEqualTo(1);
            Thread.sleep(50);
            assertThat(paused.isDone()).as("풀기 전에는 끝나지 않음").isFalse();

            pause.resume();

            assertThat(paused.get(5, TimeUnit.SECONDS)).isEqualTo(Result.DECLINED);
            // 멈추기는 다음 한 번만이다.
            assertThat(gateway.pay(REQUEST)).isEqualTo(Result.APPROVED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("다음 호출을 실패로 지정하면 그 한 번만 예외를 던지고(예상하지 못한 오류), 그 뒤로는 다시 승인한다. 초기화하면 실패 지정도 지워진다")
    void failNextAppliesToOneCall() {
        FakePaymentGateway gateway = new FakePaymentGateway();

        gateway.failNext();

        assertThatThrownBy(() -> gateway.pay(REQUEST)).isInstanceOf(IllegalStateException.class);
        assertThat(gateway.callCount()).isEqualTo(1);
        assertThat(gateway.pay(REQUEST)).isEqualTo(Result.APPROVED);

        gateway.failNext();
        gateway.reset();

        assertThat(gateway.pay(REQUEST)).isEqualTo(Result.APPROVED);
    }

    @Test
    @DisplayName("실패 지정과 멈추기를 함께 쓰면 그 호출은 멈췄다가 풀린 뒤에 예외를 던진다")
    void failNextWithPauseThrowsAfterResume() throws Exception {
        FakePaymentGateway gateway = new FakePaymentGateway();
        gateway.failNext();
        FakePaymentGateway.Pause pause = gateway.pauseNext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Result> paused = executor.submit(() -> gateway.pay(REQUEST));
            assertThat(pause.awaitEntered(Duration.ofSeconds(5))).isTrue();
            Thread.sleep(50);
            assertThat(paused.isDone()).as("풀기 전에는 끝나지 않음").isFalse();

            pause.resume();

            assertThatThrownBy(() -> paused.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("초기화하면 멈춘 호출을 풀고 아직 쓰지 않은 멈추기 지정을 지운다")
    void resetReleasesPausedCalls() throws Exception {
        FakePaymentGateway gateway = new FakePaymentGateway();
        FakePaymentGateway.Pause pause = gateway.pauseNext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Result> paused = executor.submit(() -> gateway.pay(REQUEST));
            assertThat(pause.awaitEntered(Duration.ofSeconds(5))).isTrue();
            gateway.pauseNext();

            gateway.reset();

            assertThat(paused.get(5, TimeUnit.SECONDS)).isEqualTo(Result.APPROVED);
            assertThat(executor.submit(() -> gateway.pay(REQUEST)).get(5, TimeUnit.SECONDS)).isEqualTo(Result.APPROVED);
        } finally {
            executor.shutdownNow();
        }
    }
}
