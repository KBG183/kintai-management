package com.example.kintai.domain.home;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        // トップページからサンプル画面へのリダイレクト
        return "redirect:/members/sample";
    }
}
