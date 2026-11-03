package com.example.kintai.domain.payroll.controller;

import com.example.kintai.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 給与計算・台帳管理 画面遷移コントローラー
 */
@Controller
public class PayrollController {

    /**
     * 給与台帳画面表示
     */
    @GetMapping("/payroll")
    public String payrollListView(
            @RequestParam(name = "storeId", required = false) Long storeId,
            @RequestParam(name = "yearMonth", required = false) String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model
    ) {
        Long targetStoreId = (userDetails != null && userDetails.getStoreId() != null)
                ? userDetails.getStoreId()
                : (storeId != null ? storeId : 1L);

        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        model.addAttribute("storeId", targetStoreId);
        model.addAttribute("targetYearMonth", yearMonth);

        if (userDetails != null) {
            model.addAttribute("loginUserName", userDetails.getName());
            model.addAttribute("loginUserRole", userDetails.getRole());
        }
        return "payroll/list";
    }
}
