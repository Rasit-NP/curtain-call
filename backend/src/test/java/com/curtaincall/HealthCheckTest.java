package com.curtaincall;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

import com.curtaincall.support.IntegrationTest;

/** spec 6.10: 공개 경로라 인증 Issue 뒤에도 토큰 없이 200이다. */
class HealthCheckTest extends IntegrationTest {

    @Test
    @DisplayName("GET /actuator/health는 200 {\"status\":\"UP\"}이다")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }
}
