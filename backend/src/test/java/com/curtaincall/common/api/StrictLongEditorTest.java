package com.curtaincall.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 경로·쿼리의 long 값 해석(결정 0090, 0091). ASCII 10진 정수 표기({@code [+-]?[0-9]+})이고 long 범위 안인 값만 받는다.
 * Spring 기본 해석(NumberUtils.parseNumber)은 공백을 모두 지우고 0x·#을 16진수로 읽으며, Long.parseLong은 ASCII가 아닌 숫자도 읽는다.
 */
class StrictLongEditorTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(delimiter = '|', value = {
            "0|0", "1|1", "42|42", "-1|-1",
            "+2|2", "02|2", "007|7", "+02|2",
            "-0|0", "+0|0", "00|0",
            "9223372036854775807|9223372036854775807", "-9223372036854775808|-9223372036854775808",
    })
    @DisplayName("ASCII 10진 정수 표기이고 long 범위 안이면 그 수다(+ 부호와 앞자리 0도 받는다)")
    void acceptsAsciiDecimal(String text, long expected) {
        assertThat(parse(new StrictLongEditor(false), text)).isEqualTo(expected);
        assertThat(parse(new StrictLongEditor(true), text)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            // 공백: 기본 해석은 모두 지운다("1 0"은 10이 됐다).
            " 2", "2 ", "0 2", "1 0", "\t2", "2\n", "2 ", " ",
            // 16진수: 기본 해석은 0x·0X·#를 16진수로 읽는다.
            "0x2", "0X2", "#2", "-0x2", "+#2",
            // ASCII가 아닌 숫자: Long.parseLong은 이것도 2로 읽는다(전각, 아랍-인도, 데바나가리).
            "２", "٢", "२", "1０",
            // 정수가 아님.
            "abc", "1.5", "1e3", "1L", "1_000", "+", "-", "+-1", "--1", "1-",
            // long 범위 밖.
            "9223372036854775808", "-9223372036854775809", "99999999999999999999",
    })
    @DisplayName("ASCII 10진 정수 표기가 아니거나 long 범위 밖이면 해석에 실패한다(공백, 16진수, ASCII가 아닌 숫자 포함)")
    void rejectsOtherNotations(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> new StrictLongEditor(false).setAsText(text));
        assertThatIllegalArgumentException().isThrownBy(() -> new StrictLongEditor(true).setAsText(text));
    }

    @Test
    @DisplayName("빈 문자열은 Long이면 값 없음(null)이고 long이면 해석에 실패한다(Spring 기본 편집기와 같다)")
    void emptyText() {
        assertThat(parse(new StrictLongEditor(true), "")).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new StrictLongEditor(false).setAsText(""));
    }

    private static Object parse(StrictLongEditor editor, String text) {
        editor.setAsText(text);
        return editor.getValue();
    }
}
