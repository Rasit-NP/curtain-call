package com.curtaincall.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 좌석 목록 순서의 row 비교(spec 5: row는 문자열 코드 포인트 순서). 공연 상세는 DB(seat_row COLLATE "C", UTF-8 바이트 순서 =
 * 코드 포인트 순서)가 정렬하고, 선점 응답은 잠근 좌석을 자바에서 정렬하므로 두 순서가 같아야 한다. 이 테스트는 자바 비교만 본다.
 * 두 응답의 순서가 실제로 같은지(DB 정렬 포함)는 통합 테스트 SeatListOrderTest가, row, number 전체 순서는 선점 API 통합 테스트가 확인한다.
 */
class SeatOrderTest {

    @Test
    @DisplayName("좌석 row는 코드 포인트 순서로 비교한다(보충 문자는 String.compareTo와 달리 BMP의 마지막 글자들보다 뒤)")
    void rowsCompareByCodePoint() {
        String bmpLast = "�";
        String supplementary = "🎭"; // U+1F3AD

        // String.compareTo는 UTF-16 단위로 비교해 보충 문자(대리 쌍 D83C...)를 U+FFFD보다 앞에 둔다.
        assertThat(bmpLast.compareTo(supplementary)).isPositive();
        assertThat(SeatOrder.compareRows(bmpLast, supplementary)).isNegative();
        assertThat(SeatOrder.compareRows(supplementary, bmpLast)).isPositive();
        assertThat(SeatOrder.compareRows("A", "B")).isNegative();
        assertThat(SeatOrder.compareRows("A", "AA")).isNegative();
        assertThat(SeatOrder.compareRows("B", "B")).isZero();
    }
}
