package com.example.kintai.domain.payroll.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Controller
public class PayrollController {

    @GetMapping("/payroll")
    public String payrollListView(
            @RequestParam(name = "storeId", defaultValue = "1") Long storeId,
            @RequestParam(name = "yearMonth", required = false) String yearMonth,
            Model model
    ) {
        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        model.addAttribute("storeId", storeId);
        model.addAttribute("targetYearMonth", yearMonth);
        return "payroll/list";
    }
}
