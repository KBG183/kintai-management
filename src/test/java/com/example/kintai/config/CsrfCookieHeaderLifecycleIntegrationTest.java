package com.example.kintai.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("CSRF 実Cookie・Header往復およびライフサイクル統合テスト")
class CsrfCookieHeaderLifecycleIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("1. 未認証GETリクエストにより実レスポンスCookie 'XSRF-TOKEN' が確実に発行されること")
    void getRequest_ShouldIssueXsrfCookie() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("2. モック(.with(csrf()))を一切使わず、GETで発行された実CookieをHeader(X-XSRF-TOKEN)に完全一致させてPOSTした場合、CSRFを突破して403にならないこと")
    void postWithRealCookieAndMatchingHeader_ShouldPassCsrf() throws Exception {
        // Step 1: 未認証状態でGET /login を叩き、実際の生XSRF-TOKENクッキーを取得
        MvcResult getResult = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = getResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie, "XSRF-TOKENクッキーが発行されている必要があります");
        String rawToken = xsrfCookie.getValue();
        assertTrue(rawToken != null && !rawToken.isBlank(), "生トークン値が空であってはなりません");

        // Step 2: 発行された実Cookieと同一の値をヘッダー 'X-XSRF-TOKEN' に付与してPOST送信
        // （.with(csrf()) は一切使わず、SpaCsrfTokenRequestHandler の実動作を直接検証）
        MvcResult postResult = mockMvc.perform(post("/api/attendance/punch")
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andReturn();

        int httpStatus = postResult.getResponse().getStatus();
        // CSRFチェックを突破したことの証明: 403 Forbiddenではなく、未認証401/302または業務ステータスとなること
        assertTrue(httpStatus != 403, "実Cookieと一致するX-XSRF-TOKENヘッダーを送信した場合、403 Forbiddenで拒否されてはなりません（実ステータス: " + httpStatus + "）");
    }

    @Test
    @DisplayName("3. 実CookieがあってもHeader(X-XSRF-TOKEN)の値が不一致または改ざんされている場合は403 Forbiddenで確実に拒否されること")
    void postWithTamperedHeaderToken_ShouldReturn403Forbidden() throws Exception {
        MvcResult getResult = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = getResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie);

        mockMvc.perform(post("/api/attendance/punch")
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", "tampered-token-value-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. ログアウト実行時にセッションが無効化され、安全にリダイレクトされること")
    void logout_ShouldInvalidateSession() throws Exception {
        MvcResult getResult = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = getResult.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession session = (MockHttpSession) getResult.getRequest().getSession();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().is3xxRedirection());

        assertTrue(session.isInvalid(), "ログアウト後はHTTPセッションが無効化されていること");
    }
}
