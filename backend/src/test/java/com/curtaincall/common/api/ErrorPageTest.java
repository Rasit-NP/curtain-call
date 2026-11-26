package com.curtaincall.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import com.curtaincall.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring MVC 밖(서블릿 필터·컨테이너 단계)에서 난 오류도 ProblemDetail + code로 응답한다(spec 3.1, PR #8에서 넘겨받은 일).
 *
 * <p>MockMvc는 서블릿 컨테이너의 오류 페이지(/error) 전달을 흉내 내지 않으므로 실제 서버를 띄운다.
 * {@code %zz} 같은 잘못된 퍼센트 인코딩은 {@code java.net.URI}가 거부하므로 소켓으로 요청을 직접 보낸다.
 * 공개 경로(GET /api/performances/**)에서 난 오류가 /error를 거치며 401로 바뀌지 않는지도 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ErrorPageTest.ProbeController.class, ErrorPageTest.FailingFilterConfig.class})
class ErrorPageTest extends IntegrationTest {

    /** 공개 경로 아래의 시험용 경로. 이 테스트 컨텍스트에만 등록된다. */
    private static final String PUBLIC_PROBE = "/api/performances/error-page-probe";
    private static final String FAILING_FILTER_PATH = "/api/performances/failing-filter-probe";
    private static final String FILTER_SECRET = "필터 내부 상태 노출 금지";

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("공개 경로의 쿼리 값 퍼센트 인코딩이 잘못되면(?x=%zz) 400 INVALID_INPUT이다(401이나 500이 아니다)")
    void malformedQueryEncoding() throws IOException {
        RawResponse response = send("GET " + PUBLIC_PROBE + "?x=%zz HTTP/1.0\r\nHost: localhost\r\n\r\n");

        response.assertProblem(400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("공개 경로의 경로 퍼센트 인코딩이 잘못되면 400 INVALID_INPUT이다")
    void malformedPathEncoding() throws IOException {
        RawResponse response = send("GET /api/performances/%zz HTTP/1.0\r\nHost: localhost\r\n\r\n");

        response.assertProblem(400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("보안 방화벽이 거부하는 경로(인코딩된 슬래시 등)는 400 INVALID_INPUT이다")
    void firewallRejectedPath() throws IOException {
        RawResponse response = send("GET /api/performances/a%2F..%2Fb HTTP/1.0\r\nHost: localhost\r\n\r\n");

        response.assertProblem(400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("폼 본문의 퍼센트 인코딩이 잘못되면(로그인에 폼 본문) 400 INVALID_INPUT이다")
    void malformedFormBody() throws IOException {
        String body = "username=%zz&password=x";
        RawResponse response = send("POST /api/auth/login HTTP/1.0\r\nHost: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + body.length() + "\r\n\r\n" + body);

        response.assertProblem(400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("서블릿 필터에서 난 예외는 500 INTERNAL_ERROR이고 본문에 예외 내용이 없다")
    void exceptionInFilter() throws IOException {
        RawResponse response = send("GET " + FAILING_FILTER_PATH + " HTTP/1.0\r\nHost: localhost\r\n\r\n");

        response.assertProblem(500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain(FILTER_SECRET).doesNotContain("IllegalStateException").doesNotContain("trace");
    }

    @Test
    @DisplayName("비공개 경로에서는 오류보다 토큰 검사(401 AUTH_REQUIRED)가 먼저다")
    void privatePathChecksTokenFirst() throws IOException {
        RawResponse response = send("GET /api/holds?x=%zz HTTP/1.0\r\nHost: localhost\r\n\r\n");

        response.assertProblem(401, "AUTH_REQUIRED");
    }

    @Test
    @DisplayName("/error를 직접 부르면 다른 비공개 경로처럼 토큰 없이 401 AUTH_REQUIRED다")
    void errorPathItselfRequiresToken() throws IOException {
        RawResponse response = send("GET /error HTTP/1.0\r\nHost: localhost\r\n\r\n");

        response.assertProblem(401, "AUTH_REQUIRED");
    }

    private RawResponse send(String rawRequest) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(rawRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return RawResponse.parse(buffer.toString(StandardCharsets.UTF_8));
        }
    }

    record RawResponse(int status, String headers, String body) {

        static RawResponse parse(String raw) {
            int split = raw.indexOf("\r\n\r\n");
            String head = split >= 0 ? raw.substring(0, split) : raw;
            String body = split >= 0 ? raw.substring(split + 4) : "";
            int status = Integer.parseInt(head.split(" ", 3)[1]);
            return new RawResponse(status, head, body);
        }

        void assertProblem(int expectedStatus, String code) {
            assertThat(status).as("상태 코드. 응답:\n%s\n\n%s", headers, body).isEqualTo(expectedStatus);
            assertThat(headers.toLowerCase()).contains("content-type: application/problem+json");
            assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(expectedStatus);
            assertThat(JsonPath.<String>read(body, "$.code")).isEqualTo(code);
            assertThat(JsonPath.<String>read(body, "$.detail")).isNotBlank();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping(PUBLIC_PROBE)
        String probe(@RequestParam(required = false) String x) {
            return "x=" + x;
        }
    }

    /** 특정 경로에서 예외를 던지는 서블릿 필터. */
    static class FailingFilterConfig {

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> failingFilter() {
            FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    if (FAILING_FILTER_PATH.equals(request.getRequestURI())) {
                        throw new IllegalStateException(FILTER_SECRET);
                    }
                    chain.doFilter(request, response);
                }
            });
            registration.setOrder(Ordered.LOWEST_PRECEDENCE);
            return registration;
        }
    }
}
