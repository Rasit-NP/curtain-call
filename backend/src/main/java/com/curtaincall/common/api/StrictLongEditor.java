package com.curtaincall.common.api;

import java.beans.PropertyEditorSupport;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * 경로·쿼리 값(문자열)을 long·Long으로 해석한다(결정 0090, 0091). ASCII 10진 정수 표기({@code [+-]?[0-9]+})이고 long 범위 안인
 * 값만 받는다. {@code +2}, {@code 02}는 2다.
 *
 * <p>Spring 기본 해석(NumberUtils.parseNumber)은 공백을 모두 지우고({@code "1 0"} → 10), {@code 0x}·{@code #}로 시작하면
 * 16진수로 읽으며, 그 뒤의 Long.parseLong은 ASCII가 아닌 숫자(전각 {@code ２} 등)도 읽는다. 이 편집기는 그런 값을 바꿔 받지 않고
 * 해석에 실패한다. 실패하면 Spring MVC가 인자 형식 오류로 바꾸고, 공통 처리기가 400 INVALID_INPUT으로 응답한다(spec 3.1, 결정 0012).
 *
 * <p>{@link StrictLongBinding}이 모든 컨트롤러의 바인더에 등록한다. 편집기는 상태를 가지므로 바인더마다 새로 만든다.
 */
public final class StrictLongEditor extends PropertyEditorSupport {

    private static final Pattern ASCII_DECIMAL = Pattern.compile("[+-]?[0-9]+");

    private final boolean allowEmpty;

    /**
     * @param allowEmpty 빈 문자열을 값 없음(null)으로 받을지. Long이면 true, long이면 false다. Spring 기본 편집기
     *     (CustomNumberEditor)와 같게 두어, 빈 쿼리 값({@code ?x=})의 처리(필수면 400, 선택이면 값 없음)를 바꾸지 않는다.
     */
    public StrictLongEditor(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }

    /** @throws IllegalArgumentException ASCII 10진 정수 표기가 아니거나 long 범위 밖이면(범위 밖은 NumberFormatException) */
    @Override
    public void setAsText(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            if (!this.allowEmpty) {
                throw new IllegalArgumentException("값이 없다");
            }
            setValue(null);
            return;
        }
        if (!ASCII_DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException("ASCII 10진 정수 표기가 아니다");
        }
        setValue(Long.parseLong(text));
    }
}
