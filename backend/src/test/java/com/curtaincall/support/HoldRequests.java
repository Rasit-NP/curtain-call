package com.curtaincall.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.UnsupportedEncodingException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * 선점 API(생성 {@code POST /api/holds}, 조회 {@code GET}·해제 {@code DELETE /api/holds/{holdId}}) 요청 도우미.
 * 인증은 {@link AuthRequests#asUser(long)}로 붙인다.
 *
 * <pre>{@code
 * long holdId = HoldRequests.holdId(mockMvc.perform(HoldRequests.hold(userId, performanceId, seatA, seatB))
 *         .andExpect(status().isCreated()).andReturn());
 * }</pre>
 */
public final class HoldRequests {

    private HoldRequests() {
    }

    /** 사용자 userId로 공연의 좌석 묶음을 선점하는 요청. */
    public static MockHttpServletRequestBuilder hold(long userId, long performanceId, long... seatIds) {
        return post("/api/holds")
                .with(AuthRequests.asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(performanceId, seatIds));
    }

    /** 사용자 userId로 선점을 조회하는 요청(spec 6.5). */
    public static MockHttpServletRequestBuilder getHold(long userId, long holdId) {
        return get("/api/holds/{holdId}", holdId).with(AuthRequests.asUser(userId));
    }

    /** 사용자 userId로 선점을 해제하는 요청(spec 6.6). */
    public static MockHttpServletRequestBuilder release(long userId, long holdId) {
        return delete("/api/holds/{holdId}", holdId).with(AuthRequests.asUser(userId));
    }

    /** spec 6.4 요청 본문: {@code {"performanceId": 1, "seatIds": [101, 104]}}. */
    public static String body(long performanceId, long... seatIds) {
        String ids = LongStream.of(seatIds).mapToObj(Long::toString).collect(Collectors.joining(",", "[", "]"));
        return "{\"performanceId\":" + performanceId + ",\"seatIds\":" + ids + "}";
    }

    /** 선점 응답(201 Hold)의 id. */
    public static long holdId(MvcResult result) {
        try {
            Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
            return id.longValue();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 오류 응답의 code. */
    public static String code(MvcResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
