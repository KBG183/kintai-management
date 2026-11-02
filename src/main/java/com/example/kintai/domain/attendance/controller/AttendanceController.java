package com.example.kintai.domain.attendance.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AttendanceController {

    @GetMapping("/attendance")
    public String attendancePunchView(
            @RequestParam(name = "storeStaffId", defaultValue = "1") Long storeStaffId,
            Model model
    ) {
        model.addAttribute("storeStaffId", storeStaffId);
        return "attendance/punch";
    }

    @GetMapping("/attendance/mod-requests")
    public String attendanceModListView(
            @RequestParam(name = "storeId", defaultValue = "1") Long storeId,
            Model model
    ) {
        model.addAttribute("storeId", storeId);
        return "attendance/mod-list";
    }
}
