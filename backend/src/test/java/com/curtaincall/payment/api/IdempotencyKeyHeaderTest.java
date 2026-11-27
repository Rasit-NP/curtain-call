package com.curtaincall.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;

/**
 * R5 헤더 검증(spec 6.7 오류 표 2번, 결정 0141): 키는 ASCII 출력 문자(0x21~0x7E) 1~100자이고, 헤더는 하나여야 한다.
 * 그 밖은 400 INVALID_INPUT이다. 헤더 값 목록(서블릿이 준 그대로)을 넘겨 스프링 컨텍스트 없이 확인한다.
 */
class IdempotencyKeyHeaderTest {

    @Test
    @DisplayName("R5: UUID와 ASCII 출력 문자의 양 끝(! ~), 1자 키를 받는다")
    void acceptsVisibleAscii() {
        String uuid = UUID.randomUUID().toString();

        assertThat(IdempotencyKeyHeader.parse(List.of(uuid))).isEqualTo(uuid);
        assertThat(IdempotencyKeyHeader.parse(List.of("!~"))).isEqualTo("!~");
        assertThat(IdempotencyKeyHeader.parse(List.of("a"))).isEqualTo("a");
    }

    @Test
    @DisplayName("R5: 키 길이 100자는 받고 101자는 400 INVALID_INPUT이다")
    void hundredCharactersAtMost() {
        String hundred = "k".repeat(100);

        assertThat(IdempotencyKeyHeader.parse(List.of(hundred))).isEqualTo(hundred);
        assertInvalid(List.of(hundred + "k"));
    }

    @Test
    @DisplayName("R5: 키가 없거나 비었으면 400 INVALID_INPUT이다")
    void missingOrEmptyIsInvalid() {
        assertInvalid(List.of());
        assertInvalid(List.of(""));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {" ", "   ", "\t", " \t ", "a b", " abc", "abc ", "ab\tc"})
    @DisplayName("R5: 공백뿐인 키와 공백·탭이 들어 있는 키는 400 INVALID_INPUT이다")
    void whitespaceIsInvalid(String key) {
        assertInvalid(List.of(key));
    }

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(strings = {"0000", "0001", "001F", "007F", "0080", "00A0", "00E9", "3000", "D0A4"})
    @DisplayName("R5: 제어 문자와 ASCII가 아닌 문자(유니코드 공백, 한글 포함)가 들어 있는 키는 400 INVALID_INPUT이다")
    void controlAndNonAsciiAreInvalid(String hex) {
        String character = Character.toString(Integer.parseInt(hex, 16));

        assertInvalid(List.of(character));
        assertInvalid(List.of("key" + character));
    }

    @Test
    @DisplayName("R5: Idempotency-Key 헤더가 두 개 이상이면 값이 모두 올바르거나 같아도 400 INVALID_INPUT이다")
    void moreThanOneHeaderIsInvalid() {
        assertInvalid(List.of("key-1", "key-2"));
        assertInvalid(List.of("key-1", "key-1"));
        assertInvalid(List.of("key-1", ""));
    }

    private static void assertInvalid(List<String> values) {
        assertThatThrownBy(() -> IdempotencyKeyHeader.parse(values))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
