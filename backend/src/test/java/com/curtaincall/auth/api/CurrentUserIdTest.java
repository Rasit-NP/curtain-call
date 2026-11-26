package com.curtaincall.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.support.AuthRequests;
import com.curtaincall.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * 컨트롤러가 현재 사용자 id를 얻는 방법(@CurrentUserId = 액세스 토큰의 sub)과, 기능 테스트가 쓸 요청 도우미
 * {@link AuthRequests#asUser(long)}를 확인한다. 기능 API가 아직 없어 이 테스트 안의 컨트롤러로 확인한다.
 */
@Import(CurrentUserIdTest.WhoAmIController.class)
class CurrentUserIdTest extends IntegrationTest {

    private static final String WHO_AM_I = "/api/test/who-am-i";

    @Test
    @DisplayName("로그인 API로 받은 토큰이면 @CurrentUserId는 그 사용자의 id다")
    void loginTokenGivesUserId() throws Exception {
        long userId = testUsers.create("alice", "alice-password");
        testUsers.create("bob", "bob-password");
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"alice-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get(WHO_AM_I).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(userId)));
    }

    @Test
    @DisplayName("요청 도우미 AuthRequests.asUser(id)로 만든 요청이면 @CurrentUserId는 그 id다")
    void helperGivesUserId() throws Exception {
        mockMvc.perform(get(WHO_AM_I).with(AuthRequests.asUser(7L)))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    /** 현재 사용자 id를 그대로 돌려주는 시험용 컨트롤러. 이 테스트 컨텍스트에만 등록된다. */
    @RestController
    static class WhoAmIController {

        @GetMapping(WHO_AM_I)
        String whoAmI(@CurrentUserId long userId) {
            return String.valueOf(userId);
        }
    }
}
