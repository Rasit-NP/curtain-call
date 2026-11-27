package com.curtaincall.reservation.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPayments;
import com.curtaincall.support.TestPerformances;

/**
 * 예매·결제 테이블의 DB 제약(spec 2.1, 2.3, 결정 0140). 잠금(결정 0100)이 막는 일을 응용 코드가 놓쳐도 DB가 한 번 더 막는지 SQL로 직접 넣어 확인한다.
 */
class ReservationSchemaTest extends IntegrationTest {

    private static final Instant AT = Instant.parse("2026-12-01T10:00:00Z");

    @Autowired
    private TestPerformances performances;

    @Autowired
    private TestHolds holds;

    @Autowired
    private TestPayments payments;

    private long user;
    private long performance;
    private long seat;
    private long firstHold;
    private long secondHold;

    @BeforeEach
    void createHolds() {
        user = testUsers.create("schema-user", "schema-password");
        long venue = performances.createVenue("제약홀");
        performance = performances.createPerformance("제약 공연", venue, Instant.parse("2026-12-20T10:00:00Z"), 30000);
        seat = performances.addSeat(performance, "A", 1, 1, 1);
        firstHold = holds.insertHold(user, performance, "PAID", AT, AT.plusSeconds(300));
        secondHold = holds.insertHold(user, performance, "PAID", AT, AT.plusSeconds(300));
    }

    @Test
    @DisplayName("한 선점으로 예매는 하나만 만들 수 있다")
    void oneReservationPerHold() {
        payments.insertReservation(user, performance, firstHold, 30000, AT);

        assertThatThrownBy(() -> payments.insertReservation(user, performance, firstHold, 30000, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 좌석은 예매 하나에만 들어간다(같은 좌석을 두 번 팔 수 없다)")
    void oneReservationPerSeat() {
        long first = payments.insertReservation(user, performance, firstHold, 30000, AT);
        long second = payments.insertReservation(user, performance, secondHold, 30000, AT);
        payments.insertReservationSeat(first, seat);

        assertThatThrownBy(() -> payments.insertReservationSeat(second, seat)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("결제는 SUCCEEDED일 때만 예매가 있고 FAILED이면 없다. 상태는 SUCCEEDED·FAILED만 쓸 수 있다")
    void paymentHasReservationOnlyWhenSucceeded() {
        long reservation = payments.insertReservation(user, performance, firstHold, 30000, AT);

        assertThatCode(() -> payments.insertPayment(user, firstHold, 30000, "SUCCEEDED", reservation, AT)).doesNotThrowAnyException();
        assertThatCode(() -> payments.insertPayment(user, secondHold, 30000, "FAILED", null, AT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> payments.insertPayment(user, secondHold, 30000, "SUCCEEDED", null, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payments.insertPayment(user, secondHold, 30000, "FAILED", reservation, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payments.insertPayment(user, secondHold, 30000, "PENDING", null, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("예매 하나에 승인된 결제는 하나다")
    void oneSucceededPaymentPerReservation() {
        long reservation = payments.insertReservation(user, performance, firstHold, 30000, AT);
        payments.insertPayment(user, firstHold, 30000, "SUCCEEDED", reservation, AT);

        assertThatThrownBy(() -> payments.insertPayment(user, firstHold, 30000, "SUCCEEDED", reservation, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
