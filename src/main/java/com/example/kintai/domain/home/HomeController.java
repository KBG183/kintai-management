package com.example.kintai.domain.home;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ホームおよび初期ルート（/）コントローラー
 */
@Controller
public class HomeController {

    /**
     * ルートURLアクセス時の振り分け処理
     */
    @GetMapping("/")
    public String index(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "redirect:/login";
        }

        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));

        return isOwner ? "redirect:/attendance/mod-requests" : "redirect:/attendance";
    }
}
