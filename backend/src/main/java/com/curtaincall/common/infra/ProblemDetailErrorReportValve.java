package com.curtaincall.common.infra;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.curtaincall.common.api.ErrorCode;

/**
 * Tomcat이 요청을 앱(서블릿)에 넘기기 전에 거부한 오류를 ProblemDetail + code로 쓴다(spec 3.1).
 * 예: 경로의 잘못된 퍼센트 인코딩(/%zz), 인코딩된 슬래시(%2F). 이런 요청은 /error에도 가지 않아 Tomcat 기본 HTML이 나간다.
 *
 * <p>앱에 닿은 요청의 오류는 이미 본문이 쓰여 있어 이 밸브가 건드리지 않는다(Tomcat ErrorReportValve와 같은 조건).
 * 상태 → code는 다른 오류와 같은 규칙이다({@link ErrorCode#forFrameworkStatus(int)}). 본문에 예외 내용을 넣지 않는다.
 */
public class ProblemDetailErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int status = response.getStatus();
        // ErrorReportValve.report와 같은 조건: 오류가 아니거나, 본문이 이미 있거나, 이미 보고했으면 건드리지 않는다.
        if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        ErrorCode code = ErrorCode.forFrameworkStatus(status);
        HttpStatus httpStatus = code.status();
        try {
            response.setStatus(httpStatus.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Writer writer = response.getReporter();
            if (writer != null) {
                // 값은 모두 코드 안의 상수다(요청 내용을 넣지 않는다). 따옴표·역슬래시가 없어 이스케이프가 필요 없다.
                writer.write("{\"type\":\"about:blank\",\"title\":\"" + httpStatus.getReasonPhrase()
                        + "\",\"status\":" + httpStatus.value()
                        + ",\"detail\":\"" + code.defaultDetail()
                        + "\",\"code\":\"" + code.name() + "\"}");
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            // 응답을 쓰지 못하면 Tomcat 기본 처리에 맡긴다(연결이 끊긴 경우 등).
        }
    }
}
