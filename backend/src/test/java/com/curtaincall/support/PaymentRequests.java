package com.curtaincall.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * 결제 API({@code POST /api/payments})와 내 예매 API({@code GET /api/reservations}) 요청 도우미. 인증은 {@link AuthRequests#asUser(long)}로 붙인다.
 *
 * <pre>{@code
 * mockMvc.perform(PaymentRequests.pay(userId, holdId)).andExpect(status().isCreated());
 * }</pre>
 */
public final class PaymentRequests {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private PaymentRequests() {
    }

    /** 사용자 userId로 선점을 결제하는 요청. 멱등 키는 결제 시도마다 새로 만든다(UUID, 프론트엔드와 같다). */
    public static MockHttpServletRequestBuilder pay(long userId, long holdId) {
        return pay(userId, holdId, UUID.randomUUID().toString());
    }

    /** 사용자 userId로 선점을 결제하는 요청(멱등 키를 정함). */
    public static MockHttpServletRequestBuilder pay(long userId, long holdId, String idempotencyKey) {
        return post("/api/payments")
                .with(AuthRequests.asUser(userId))
                .header(IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(holdId));
    }

    /** 사용자 userId의 내 예매 요청(spec 6.8). */
    public static MockHttpServletRequestBuilder reservations(long userId) {
        return get("/api/reservations").with(AuthRequests.asUser(userId));
    }

    /** spec 6.7 요청 본문: {@code {"holdId": 31}}. */
    public static String body(long holdId) {
        return "{\"holdId\":" + holdId + "}";
    }

    /** 결제 응답(201)의 예매 id. */
    public static long reservationId(MvcResult result) {
        return readLong(result, "$.reservation.id");
    }

    /** 결제 응답(201)의 결제 id. */
    public static long paymentId(MvcResult result) {
        return readLong(result, "$.paymentId");
    }

    private static long readLong(MvcResult result, String path) {
        try {
            Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
            return value.longValue();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
