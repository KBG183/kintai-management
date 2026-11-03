package com.example.kintai.domain.attendance.controller;

import com.example.kintai.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 勤怠管理 画面遷移コントローラー
 */
@Controller
public class AttendanceController {

    /**
     * 打刻画面表示
     */
    @GetMapping("/attendance")
    public String attendancePunchView(
            @RequestParam(name = "storeStaffId", required = false) Long storeStaffId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model
    ) {
        Long targetStaffId = (userDetails != null && userDetails.getStoreStaffId() != null)
                ? userDetails.getStoreStaffId()
                : (storeStaffId != null ? storeStaffId : 1L);

        model.addAttribute("storeStaffId", targetStaffId);
        if (userDetails != null) {
            model.addAttribute("loginUserName", userDetails.getName());
            model.addAttribute("loginUserRole", userDetails.getRole());
        }
        return "attendance/punch";
    }

    /**
     * 勤怠修正申請一覧（オーナー承認画面）表示
     */
    @GetMapping("/attendance/mod-requests")
    public String attendanceModListView(
            @RequestParam(name = "storeId", required = false) Long storeId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model
    ) {
        Long targetStoreId = (userDetails != null && userDetails.getStoreId() != null)
                ? userDetails.getStoreId()
                : (storeId != null ? storeId : 1L);

        model.addAttribute("storeId", targetStoreId);
        if (userDetails != null) {
            model.addAttribute("loginUserName", userDetails.getName());
            model.addAttribute("loginUserRole", userDetails.getRole());
        }
        return "attendance/mod-list";
    }
}
