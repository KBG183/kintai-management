package com.example.kintai.domain.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 認証・認可関連 画面遷移コントローラー
 */
@Controller
public class AuthController {

    /**
     * ログイン画面表示
     */
    @GetMapping("/login")
    public String loginView(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "denied", required = false) String denied,
            Authentication authentication,
            Model model
    ) {
        // 既に認証済みの場合はロールに応じたダッシュボードへリダイレクト
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            boolean isOwner = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
            return isOwner ? "redirect:/attendance/mod-requests" : "redirect:/attendance";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "メールアドレスまたはパスワードが正しくありません。");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "正常にログアウトしました。");
        }
        if (denied != null) {
            model.addAttribute("errorMessage", "アクセス権限がありません。管理者アカウントでログインしてください。");
        }

        return "auth/login";
    }
}
