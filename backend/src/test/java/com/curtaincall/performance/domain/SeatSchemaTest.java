package com.curtaincall.performance.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.curtaincall.support.IntegrationTest;
import com.curtaincall.support.TestPerformances;

/**
 * 좌석과 공연장 배치의 DB 제약(spec 2.1). 같은 공연(공연장) 안에서 (row, number)가 고유하고 (gridRow, gridColumn)도 겹치지 않는다.
 * 응용 코드의 검사가 아니라 DB 제약으로 막히는지 SQL로 직접 넣어 확인한다.
 */
class SeatSchemaTest extends IntegrationTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-20T10:00:00Z");

    @Autowired
    private TestPerformances performances;

    private long venue;
    private long performance;

    @BeforeEach
    void createPerformance() {
        venue = performances.createVenue("제약홀");
        performance = performances.createPerformance("제약 공연", venue, STARTS_AT, 30000);
    }

    @Test
    @DisplayName("같은 공연에서 row·number가 같은 좌석은 위치가 달라도 저장할 수 없다")
    void seatRowNumberIsUniquePerPerformance() {
        performances.addSeat(performance, "A", 1, 1, 1);

        assertThatThrownBy(() -> performances.addSeat(performance, "A", 1, 2, 5))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("같은 공연에서 gridRow·gridColumn이 같은 좌석은 이름이 달라도 저장할 수 없다")
    void seatGridPositionIsUniquePerPerformance() {
        performances.addSeat(performance, "A", 1, 1, 1);

        assertThatThrownBy(() -> performances.addSeat(performance, "B", 7, 1, 1))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("다른 공연이면 같은 row·number·위치의 좌석을 저장할 수 있다")
    void sameSeatInOtherPerformanceIsAllowed() {
        long other = performances.createPerformance("다른 회차", venue, STARTS_AT.plusSeconds(86400), 30000);
        performances.addSeat(performance, "A", 1, 1, 1);

        assertThatCode(() -> performances.addSeat(other, "A", 1, 1, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("공연장 배치도 같은 공연장에서 row·number와 gridRow·gridColumn이 겹치지 않고, 다른 공연장과는 겹쳐도 된다")
    void venueSeatIsUniquePerVenue() {
        long otherVenue = performances.createVenue("제약홀 2관");
        performances.addVenueSeat(venue, "A", 1, 1, 1);

        assertThatThrownBy(() -> performances.addVenueSeat(venue, "A", 1, 1, 2))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> performances.addVenueSeat(venue, "A", 2, 1, 1))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatCode(() -> performances.addVenueSeat(otherVenue, "A", 1, 1, 1)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "number={0}, gridRow={1}, gridColumn={2}")
    @CsvSource({"0, 1, 1", "1, 0, 1", "1, 1, 0", "-1, 1, 1"})
    @DisplayName("좌석 number와 gridRow·gridColumn은 1부터 센다(0 이하는 저장할 수 없다)")
    void seatNumbersStartFromOne(int number, int gridRow, int gridColumn) {
        assertThatThrownBy(() -> performances.addSeat(performance, "A", number, gridRow, gridColumn))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> performances.addVenueSeat(venue, "A", number, gridRow, gridColumn))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("좌석 row는 빈 문자열일 수 없다")
    void seatRowIsNotBlank() {
        assertThatThrownBy(() -> performances.addSeat(performance, "", 1, 1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> performances.addVenueSeat(venue, "", 1, 1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("좌석 저장 상태는 AVAILABLE·HELD·SOLD만 쓸 수 있다")
    void seatStatusIsRestricted() {
        assertThatThrownBy(() -> performances.addSeat(performance, "A", 1, 1, 1, "EXPIRED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
