package com.example.kintai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("CSRF 多層防御セキュリティ統合テスト")
class CsrfSecurityIntegrationTest {

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
    @DisplayName("CSRFトークンなしのPOSTリクエストは403 Forbiddenで拒否されること")
    @WithMockUser(username = "staff1", roles = {"STAFF"})
    void postWithoutCsrf_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRFトークンが有効でも未認証ユーザーの打刻APIアクセスはログインへ拒否されること")
    void anonymousPunchWithValidCsrf_ShouldBeRejectedBySecurityFilter() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String redirectedUrl = result.getResponse().getRedirectedUrl();
                    if (redirectedUrl == null || !redirectedUrl.endsWith("/login")) {
                        throw new AssertionError("未認証打刻はログインへ誘導される必要があります。actual=" + redirectedUrl);
                    }
                });
    }

    @Test
    @DisplayName("不正なCSRFトークンを含むPOSTリクエストは403 Forbiddenで拒否されること")
    @WithMockUser(username = "staff1", roles = {"STAFF"})
    void postWithInvalidCsrf_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .header("X-XSRF-TOKEN", "invalid-csrf-token-sample")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("オーナーおよびスタッフのアカウントで正常にログインできること")
    void testFormLoginSuccess() throws Exception {
        // オーナー (owner@kintai.com) -> /attendance/mod-requests
        mockMvc.perform(post("/login-process")
                        .with(csrf())
                        .param("email", "owner@kintai.com")
                        .param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    if (!"/attendance/mod-requests".equals(url)) {
                        throw new AssertionError("オーナーログイン後のリダイレクト先が不正です: " + url);
                    }
                });

        // スタッフ (staff1@kintai.com) -> /attendance
        mockMvc.perform(post("/login-process")
                        .with(csrf())
                        .param("email", "staff1@kintai.com")
                        .param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    if (!"/attendance".equals(url)) {
                        throw new AssertionError("スタッフログイン後のリダイレクト先が不正です: " + url);
                    }
                });
    }

    @Test
    @DisplayName("正常なCSRFトークンを付与したPOSTリクエストは403にならず正常に受け付けられること")
    @WithMockUser(username = "staff1", roles = {"STAFF"})
    void postWithValidCsrf_ShouldNotReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("CSRFトークン付きリクエストが403 Forbiddenで拒否されました。");
                    }
                });
    }
}
