package com.example.kintai.domain.member.controller;

import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.domain.member.service.MemberService;
import com.example.kintai.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 【画面サンプル】Thymeleaf共通レイアウトおよびデータバインディング確認
     * URL: GET /members/sample
     */
    @GetMapping("/members/sample")
    public String memberSampleView(Model model) {
        List<MemberDto> members = memberService.getAllMembers();
        model.addAttribute("members", members);
        return "member/sample";
    }

    /**
     * 【REST API】全スタッフ・オーナー一覧取得 (JSON)
     * URL: GET /api/members
     */
    @GetMapping("/api/members")
    @ResponseBody
    public ApiResponse<List<MemberDto>> getAllMembersApi() {
        return ApiResponse.ok(memberService.getAllMembers());
    }

    /**
     * 【REST API】単一会員情報取得
     * URL: GET /api/members/{id}
     */
    @GetMapping("/api/members/{id}")
    @ResponseBody
    public ApiResponse<MemberDto> getMemberApi(@PathVariable("id") Long id) {
        return ApiResponse.ok(memberService.getMemberById(id));
    }
}
