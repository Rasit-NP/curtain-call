package com.curtaincall.common.api;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * 모든 컨트롤러에서 경로·쿼리의 long·Long 값을 {@link StrictLongEditor}로 해석하게 하는 한곳(결정 0091).
 * 공연 상세의 performanceId와 이후의 holdId 같은 경로 id가 컨트롤러마다 따로 검사하지 않아도 같은 규칙을 따른다(결정 0090).
 *
 * <p>적용 범위는 매개변수 타입이 long·Long 하나일 때다. {@code Optional<Long>}, {@code List<Long>}, {@code long[]}처럼 감싼 타입은
 * 바인더가 MVC 변환 서비스로 바꾸고 요소를 Spring 기본 해석으로 읽어, 이 편집기를 거치지 않는다({@code 0x2}가 2가 된다).
 * 지금 그런 매개변수는 없다. 생기면 같은 규칙을 적용할지 그때 정한다(결정 0091).
 *
 * <p>MVC 변환 서비스에 변환기(String → Long)를 더하는 방법은 쓰지 않는다. 바인더는 변환 서비스가 실패하면 기본 편집기
 * (CustomNumberEditor → NumberUtils.parseNumber)로 다시 해석해, {@code 0x2}·{@code " 2"}를 결국 받아들이기 때문이다. 바인더에 직접
 * 등록한 편집기는 변환 서비스보다 먼저 쓰이고, 실패해도 다른 방법으로 다시 해석하지 않는다.
 *
 * <p>요청 본문(JSON)의 숫자는 Jackson이 읽으므로 이 편집기를 거치지 않는다(결정 0072가 따로 엄격하게 정했다).
 */
@ControllerAdvice
public class StrictLongBinding {

    @InitBinder
    public void registerStrictLongEditors(WebDataBinder binder) {
        binder.registerCustomEditor(long.class, new StrictLongEditor(false));
        binder.registerCustomEditor(Long.class, new StrictLongEditor(true));
    }
}
