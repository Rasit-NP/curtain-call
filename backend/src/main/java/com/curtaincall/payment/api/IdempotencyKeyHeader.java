package com.curtaincall.payment.api;

import java.util.Collections;
import java.util.List;

import org.springframework.security.web.firewall.RequestRejectedException;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 결제 요청의 {@code Idempotency-Key} 헤더 검증(spec 6.7 오류 표 2번, 6.7.1, 결정 0141). 통과하지 못하면 400 INVALID_INPUT이다.
 *
 * <ul>
 *   <li>헤더는 하나여야 한다. 없거나 두 개 이상이면 400이다(어느 키로 처리할지 모호하다).</li>
 *   <li>키는 ASCII 출력 문자(0x21~0x7E) 1~100자다. 공백·탭·제어 문자·ASCII가 아닌 문자가 하나라도 있으면 400이다. 그래서 '공백뿐'인 키도 400이고,
 *       글자 수는 문자 수 = 바이트 수다. HTTP 헤더 값은 바이트열이라 서버(Tomcat)가 ASCII 밖 바이트를 ISO-8859-1로 읽어,
 *       ASCII 밖 문자를 받으면 '100자'가 클라이언트의 인코딩에 따라 달라지기 때문이다.</li>
 * </ul>
 *
 * <p>키를 저장·재사용하는 것(같은 키의 결과)은 #56에서 한다. 여기서는 검증만 한다.
 *
 * <p>Spring Security 방화벽(StrictHttpFirewall)은 헤더 값을 읽는 순간 제어 문자 등이 든 값을 거부한다(RequestRejectedException). 실제 서버는
 * 헤더 바이트를 ISO-8859-1로 읽으므로 한글 키(UTF-8)가 C1 제어 문자가 든 문자열이 되어 여기에 걸린다. 그 거부도 위 규칙에 걸리는 값이므로
 * 같은 400 INVALID_INPUT으로 바꾼다({@link #read}). 바꾸지 않으면 컨트롤러에서 난 예상하지 못한 예외라 500이 된다.
 */
public final class IdempotencyKeyHeader {

    public static final String NAME = "Idempotency-Key";

    static final int MAX_LENGTH = 100;

    private IdempotencyKeyHeader() {
    }

    /** 요청에서 Idempotency-Key 헤더를 읽어 {@link #parse}로 검증한다. 방화벽이 헤더 값을 거부해도 400 INVALID_INPUT이다. */
    public static String read(HttpServletRequest request) {
        List<String> values;
        try {
            values = Collections.list(request.getHeaders(NAME));
        } catch (RequestRejectedException e) {
            throw invalid("Idempotency-Key는 공백이 없는 ASCII 문자만 쓸 수 있습니다.");
        }
        return parse(values);
    }

    /**
     * 요청의 Idempotency-Key 헤더 값 목록(받은 그대로)에서 키를 꺼낸다.
     *
     * @throws ApiException INVALID_INPUT. 헤더가 없거나 두 개 이상, 키가 비었거나 100자를 넘음, ASCII 출력 문자가 아닌 문자가 있음
     */
    public static String parse(List<String> values) {
        if (values.size() != 1) {
            throw invalid(values.isEmpty() ? "Idempotency-Key 헤더가 필요합니다." : "Idempotency-Key 헤더는 하나만 보낼 수 있습니다.");
        }
        String key = values.getFirst();
        if (key.isEmpty() || key.length() > MAX_LENGTH) {
            throw invalid("Idempotency-Key는 1~100자여야 합니다.");
        }
        if (!key.chars().allMatch(IdempotencyKeyHeader::isVisibleAscii)) {
            throw invalid("Idempotency-Key는 공백이 없는 ASCII 문자만 쓸 수 있습니다.");
        }
        return key;
    }

    private static boolean isVisibleAscii(int c) {
        return c >= 0x21 && c <= 0x7E;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(ErrorCode.INVALID_INPUT, detail);
    }
}
