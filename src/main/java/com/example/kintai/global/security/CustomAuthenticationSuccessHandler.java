package com.example.kintai.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * ログイン成功時のリダイレクトハンドラー
 * ユーザー権限（ROLE_OWNER / ROLE_STAFF）に応じて初期遷移先を動的に制御
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        boolean isOwner = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OWNER"::equals);

        if (isOwner) {
            response.sendRedirect("/attendance/mod-requests");
        } else {
            response.sendRedirect("/attendance");
        }
    }
}
