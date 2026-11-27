package com.curtaincall.hold.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestHolds;
import com.curtaincall.support.TestPerformances;

/**
 * 선점 테이블의 DB 제약(spec 2.1, 2.3). 저장 상태는 ACTIVE·RELEASED·PAID뿐이다(만료는 저장하지 않는다, R3).
 * 응용 코드가 아니라 DB 제약으로 막히는지 SQL로 직접 넣어 확인한다.
 */
class HoldSchemaTest extends IntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-12-01T10:00:00Z");

    @Autowired
    private TestPerformances performances;

    @Autowired
    private TestHolds holds;

    private long user;
    private long performance;

    @BeforeEach
    void createUserAndPerformance() {
        user = testUsers.create("schema-user", "schema-password");
        long venue = performances.createVenue("제약홀");
        performance = performances.createPerformance("제약 공연", venue, Instant.parse("2026-12-20T10:00:00Z"), 30000);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ACTIVE", "RELEASED", "PAID"})
    @DisplayName("선점 저장 상태로 ACTIVE·RELEASED·PAID를 쓸 수 있다")
    void allowedStatuses(String status) {
        assertThatCode(() -> holds.insertHold(user, performance, status, CREATED_AT, CREATED_AT.plusSeconds(300)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("선점 저장 상태는 ACTIVE·RELEASED·PAID만 쓸 수 있다(만료는 저장하지 않으므로 EXPIRED도 없다)")
    void otherStatusesAreRejected() {
        assertThatThrownBy(() -> holds.insertHold(user, performance, "EXPIRED", CREATED_AT, CREATED_AT.plusSeconds(300)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
