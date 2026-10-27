package com.example.kintai.domain.home;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        // 초기 개발 시 샘플 화면으로 바로 이동하도록 안내
        return "redirect:/members/sample";
    }
}
