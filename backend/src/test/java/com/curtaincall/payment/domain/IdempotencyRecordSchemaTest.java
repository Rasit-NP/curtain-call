package com.curtaincall.payment.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestPayments;

/**
 * 멱등 기록 테이블의 DB 제약(spec 2.1, 6.7.1, R5). 같은 사용자의 결제는 사용자 행 잠금으로 이미 한 줄로 서므로(결정 0100), (사용자, 키) 유니크 제약을
 * 빼도 동시성 테스트가 실패하지 않을 수 있다. 그래서 제약 자체를 SQL로 직접 넣어 확인한다.
 */
class IdempotencyRecordSchemaTest extends IntegrationTest {

    private static final Instant AT = Instant.parse("2026-12-01T10:00:00Z");
    private static final String BODY = "{\"paymentId\":1}";

    @Autowired
    private TestPayments payments;

    private long alice;
    private long bob;

    @BeforeEach
    void createUsers() {
        alice = testUsers.create("alice", "alice-password");
        bob = testUsers.create("bob", "bob-password");
    }

    @Test
    @DisplayName("R5: 멱등 기록은 같은 사용자·같은 키로 둘을 만들 수 없고, 다른 사용자나 다른 키면 만들 수 있다((사용자, 키) 복합 유니크)")
    void userAndKeyAreUnique() {
        payments.insertIdempotencyRecord(alice, "key-1", 10, 201, BODY, AT);

        assertThatThrownBy(() -> payments.insertIdempotencyRecord(alice, "key-1", 10, 201, BODY, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payments.insertIdempotencyRecord(alice, "key-1", 11, 404, BODY, AT))
                .as("선점이 달라도 같은 키는 하나다")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> payments.insertIdempotencyRecord(bob, "key-1", 10, 201, BODY, AT)).doesNotThrowAnyException();
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "key-2", 10, 201, BODY, AT)).doesNotThrowAnyException();
        // 키는 대소문자를 구분한다(받은 문자열 그대로).
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "KEY-1", 10, 201, BODY, AT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("저장한 응답은 상태와 본문이 함께 있거나 함께 없고, 상태는 201·402·404·409만 저장한다(400·500은 저장하지 않는다)")
    void storedResponseShape() {
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "created", 10, 201, BODY, AT)).doesNotThrowAnyException();
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "declined", 10, 402, BODY, AT)).doesNotThrowAnyException();
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "not-found", 10, 404, BODY, AT)).doesNotThrowAnyException();
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "expired", 10, 409, BODY, AT)).doesNotThrowAnyException();
        // 응답을 저장하기 전(같은 트랜잭션 안에서 키를 잡은 때)의 모양
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "claimed", 10, null, null, AT)).doesNotThrowAnyException();

        assertThatThrownBy(() -> payments.insertIdempotencyRecord(alice, "status-only", 10, 201, null, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payments.insertIdempotencyRecord(alice, "body-only", 10, null, BODY, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (int status : new int[] {200, 400, 401, 500}) {
            assertThatThrownBy(() -> payments.insertIdempotencyRecord(alice, "status-" + status, 10, status, BODY, AT))
                    .as("상태 " + status)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("멱등 기록의 선점은 없는 선점 id여도 저장한다(404도 저장한다). 사용자는 있는 사용자여야 한다(결정 0071)")
    void holdIsNotAForeignKeyButUserIs() {
        assertThatCode(() -> payments.insertIdempotencyRecord(alice, "missing-hold", 999_999, 404, BODY, AT)).doesNotThrowAnyException();

        assertThatThrownBy(() -> payments.insertIdempotencyRecord(Math.max(alice, bob) + 1000, "ghost", 10, 404, BODY, AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
