package com.example.kintai.domain.shift.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ShiftController {

    @GetMapping("/shifts")
    public String shiftCalendarView(
            @RequestParam(name = "storeId", defaultValue = "1") Long storeId,
            Model model
    ) {
        model.addAttribute("storeId", storeId);
        return "shift/calendar";
    }
}
