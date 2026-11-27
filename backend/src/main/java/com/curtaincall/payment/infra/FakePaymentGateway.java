package com.curtaincall.payment.infra;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.curtaincall.payment.domain.PaymentGateway;

/**
 * 모의 결제(spec 6.7.2). 실제 PG를 부르지 않고 승인과 거절을 흉내 낸다. 모든 프로필에서 이 구현 하나를 쓴다.
 *
 * <ul>
 *   <li>아무것도 지정하지 않으면 늘 승인한다. 테스트가 아닌 프로필(local, e2e, prod)에서는 아래 조작을 부르는 코드가 없으므로 언제나 승인한다.</li>
 *   <li>테스트 조작: 다음 호출을 거절로 지정({@link #declineNext}), 호출 횟수 확인({@link #callCount}), 다음 호출을 멈췄다가 풀기({@link #pauseNext}).
 *       "다음 호출"은 지정한 뒤 처음 들어오는 호출 하나다. 통합 테스트는 컨텍스트를 공유하므로 매 테스트 전에 {@link #reset}한다.</li>
 * </ul>
 *
 * <p>여러 요청 스레드가 함께 부르므로 상태는 원자적 값으로 둔다. JVM 안의 락으로 결제의 동시성을 보장하는 것이 아니다(그것은 DB 잠금이 한다).
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    /** 멈춘 호출을 풀어 주지 않은 채 테스트가 끝나도 요청 스레드가 영원히 남지 않게 하는 상한. 넘으면 그 결제는 500으로 끝난다. */
    static final Duration MAX_PAUSE = Duration.ofSeconds(30);

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean declineNext = new AtomicBoolean();
    private final AtomicReference<Pause> pauseNext = new AtomicReference<>();
    private final Set<Pause> issued = ConcurrentHashMap.newKeySet();

    @Override
    public Result pay(Request request) {
        calls.incrementAndGet();
        boolean decline = declineNext.getAndSet(false);
        Pause pause = pauseNext.getAndSet(null);
        if (pause != null) {
            pause.block();
        }
        return decline ? Result.DECLINED : Result.APPROVED;
    }

    /** 테스트: 다음 호출 하나를 거절한다. */
    public void declineNext() {
        declineNext.set(true);
    }

    /** 테스트: 다음 호출 하나를 돌려준 {@link Pause}가 풀릴 때까지 멈춘다. 멈춘 동안 결제는 잠금을 쥐고 있다. */
    public Pause pauseNext() {
        Pause pause = new Pause();
        issued.add(pause);
        pauseNext.set(pause);
        return pause;
    }

    /** 테스트: 마지막 초기화 뒤 부른 횟수. R4의 "만료면 모의 결제를 부르지 않는다"를 이것으로 검증한다. */
    public int callCount() {
        return calls.get();
    }

    /** 테스트: 호출 횟수와 거절·멈추기 지정을 지우고, 멈춘 호출이 있으면 푼다. */
    public void reset() {
        calls.set(0);
        declineNext.set(false);
        pauseNext.set(null);
        issued.forEach(Pause::resume);
        issued.clear();
    }

    /** 멈춘 호출 하나. 호출이 들어왔는지 기다릴 수 있고, 풀어 줄 수 있다. */
    public static final class Pause {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch resumed = new CountDownLatch(1);

        private Pause() {
        }

        /** 호출이 멈춘 지점에 들어올 때까지 기다린다. 들어왔으면 true. */
        public boolean awaitEntered(Duration timeout) throws InterruptedException {
            return entered.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        /** 멈춘 호출을 풀어 준다(들어오기 전에 부르면 들어오자마자 지나간다). */
        public void resume() {
            resumed.countDown();
        }

        private void block() {
            entered.countDown();
            try {
                if (!resumed.await(MAX_PAUSE.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("모의 결제가 " + MAX_PAUSE + " 안에 풀리지 않았다");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("모의 결제를 기다리다 중단됐다", e);
            }
        }
    }
}
