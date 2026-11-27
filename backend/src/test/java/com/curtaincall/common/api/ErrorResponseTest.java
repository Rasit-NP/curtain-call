package com.curtaincall.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.curtaincall.support.IntegrationTest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * spec 3.1 공통 오류 응답. 기능 API가 아직 없어 이 테스트 안의 컨트롤러로 오류 상황을 만든다.
 *
 * <p>보안 필터를 끈 MockMvc를 쓴다. 인증 Issue에서 Spring Security가 들어오면 공개 경로가 아닌 곳은 토큰 검사(401)가
 * 먼저 일어나는데, 이 테스트는 MVC 안의 오류 변환만 검증하므로 그 뒤에도 기대값이 바뀌지 않아야 한다.
 * 토큰 검사와의 순서(401이 먼저)는 인증 Issue의 테스트가 맡는다.
 */
@AutoConfigureMockMvc(addFilters = false)
@Import({ErrorResponseTest.ErrorProbeController.class, ErrorResponseTest.ValidatedProbeController.class})
class ErrorResponseTest extends IntegrationTest {

    private static final String BASE = "/api/test/error-probe";
    private static final String INTERNAL_MESSAGE = "내부 상태 노출 금지";

    @Value("${spring.test.mockmvc.add-filters}")
    private boolean addFilters;

    @Test
    @DisplayName("오류 변환만 검증하도록 서블릿 필터(이후 보안 필터 포함)를 끈 MockMvc를 쓴다")
    void filtersAreDisabled() {
        assertThat(addFilters).isFalse();
    }

    @Test
    @DisplayName("오류가 없으면 시험용 컨트롤러가 200을 준다")
    void probeControllerIsRegistered() throws Exception {
        mockMvc.perform(get(BASE + "/items/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("본문이 올바른 JSON이 아니면 400 INVALID_INPUT이다")
    void malformedJson() throws Exception {
        expectProblem(mockMvc.perform(post(BASE + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("경로 값의 형식이 틀리면(숫자 자리에 문자) 400 INVALID_INPUT이다")
    void malformedPathVariable() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/items/abc")), 400, "INVALID_INPUT");
    }

    /**
     * 결정 0090, 0091: long 경로 값의 해석은 한 곳(모든 컨트롤러의 바인더에 편집기를 등록하는 StrictLongBinding)에서 정해, 공연 상세의 performanceId뿐 아니라
     * 이후의 holdId 같은 모든 long 경로 값이 같은 규칙을 따른다. 이 시험용 경로는 공연 상세와 관계없는 long 경로 값이다.
     */
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0x1", "#1", " 1", "1 ", "1 0", "１"})
    @DisplayName("어느 경로든 long 경로 값은 ASCII 10진 정수 표기만 받는다(공백, 16진수, ASCII가 아닌 숫자는 400 INVALID_INPUT)")
    void pathLongAcceptsOnlyAsciiDecimalEverywhere(String id) throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/items/{id}", id)), 400, "INVALID_INPUT");
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({"+1, item-1", "01, item-1", "-1, item--1"})
    @DisplayName("어느 경로든 long 경로 값에 + 부호나 앞자리 0이 있으면 그 수다")
    void pathLongAcceptsSignAndLeadingZerosEverywhere(String id, String expected) throws Exception {
        mockMvc.perform(get(BASE + "/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    @DisplayName("쿼리 값의 형식이 틀리면 400 INVALID_INPUT이다")
    void malformedQueryParameter() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/items").param("page", "abc")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("필수 쿼리 값이 없으면 400 INVALID_INPUT이다")
    void missingQueryParameter() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/items")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("본문 필드 검증에 실패하면 400 INVALID_INPUT이다")
    void bodyFieldValidationFails() throws Exception {
        expectProblem(mockMvc.perform(post(BASE + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("쿼리 값 검증에 실패하면 400 INVALID_INPUT이다")
    void parameterValidationFails() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/items").param("page", "-1")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("클래스에 @Validated를 붙인 빈의 메서드 검증에 실패해도 400 INVALID_INPUT이다")
    void validatedBeanConstraintFails() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/validated").param("page", "-1")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 대신 400 INVALID_INPUT이다")
    void unsupportedMethod() throws Exception {
        expectProblem(mockMvc.perform(delete(BASE + "/items")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415 대신 400 INVALID_INPUT이다")
    void unsupportedContentType() throws Exception {
        expectProblem(mockMvc.perform(post(BASE + "/items")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name")), 400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("없는 경로는 404 NOT_FOUND다")
    void unknownPath() throws Exception {
        expectProblem(mockMvc.perform(get("/api/no-such-path")), 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("처리하지 못한 예외는 500 INTERNAL_ERROR이고 본문에 스택 트레이스와 예외 내용이 없다")
    void unhandledException() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/boom")), 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("ErrorProbeController"))))
                .andExpect(content().string(not(containsString(INTERNAL_MESSAGE))));
    }

    @Test
    @DisplayName("기능 코드가 ApiException으로 알린 오류는 그 코드의 상태와 code, 설명으로 응답한다")
    void apiExceptionUsesItsCode() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/api-exception")), 409, "SEAT_ALREADY_HELD")
                .andExpect(jsonPath("$.detail").value("A-1 좌석은 이미 선점되었습니다."));
    }

    @Test
    @DisplayName("코드를 정하지 않은 상태 예외(ResponseStatusException 409)는 표에 없는 조합을 만들지 않고 500 INTERNAL_ERROR다")
    void statusExceptionWithoutCodeIsInternalError() throws Exception {
        expectProblem(mockMvc.perform(get(BASE + "/status-exception")), 500, "INTERNAL_ERROR");
    }

    /** spec 3.1: ProblemDetail 형식에 code를 더한다. */
    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    record CreateItemRequest(@NotBlank String name) {
    }

    /** 오류 상황을 만드는 시험용 컨트롤러. 이 테스트 컨텍스트에만 등록된다. */
    @RestController
    @RequestMapping(BASE)
    static class ErrorProbeController {

        @GetMapping("/items/{id}")
        String item(@PathVariable long id) {
            return "item-" + id;
        }

        @GetMapping("/items")
        String items(@RequestParam @Min(0) int page) {
            return "page-" + page;
        }

        @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
        String create(@Valid @RequestBody CreateItemRequest request) {
            return request.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException(INTERNAL_MESSAGE);
        }

        @GetMapping("/api-exception")
        String apiException() {
            throw new ApiException(ErrorCode.SEAT_ALREADY_HELD, "A-1 좌석은 이미 선점되었습니다.");
        }

        @GetMapping("/status-exception")
        String statusException() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, INTERNAL_MESSAGE);
        }
    }

    /**
     * 클래스에 @Validated가 있으면 MVC 기본 검증 대신 AOP 메서드 검증이 먼저 돌아 ConstraintViolationException을 던진다.
     * 서비스에 @Validated를 붙여도 같은 예외가 나온다.
     */
    @RestController
    @Validated
    @RequestMapping(BASE)
    static class ValidatedProbeController {

        @GetMapping("/validated")
        String validated(@RequestParam @Min(0) int page) {
            return "page-" + page;
        }
    }
}
