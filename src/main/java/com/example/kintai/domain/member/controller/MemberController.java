package com.example.kintai.domain.member.controller;

import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.domain.member.dto.MemberResponseDto;
import com.example.kintai.domain.member.service.MemberService;
import com.example.kintai.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * URL: GET /members/sample (ADMIN専用に保護)
     */
    @GetMapping("/members/sample")
    @PreAuthorize("hasRole('ADMIN')")
    public String memberSampleView(Model model) {
        List<MemberResponseDto> members = memberService.getAllMembers().stream()
                .map(MemberResponseDto::from)
                .toList();
        model.addAttribute("members", members);
        return "member/sample";
    }

    /**
     * 【REST API】全スタッフ・オーナー一覧取得 (JSON, ADMIN専用)
     * URL: GET /api/members
     */
    @GetMapping("/api/members")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MemberResponseDto>> getAllMembersApi() {
        List<MemberResponseDto> responses = memberService.getAllMembers().stream()
                .map(MemberResponseDto::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    /**
     * 【REST API】単一会員情報取得 (ADMINまたは本人専用)
     * URL: GET /api/members/{id}
     */
    @GetMapping("/api/members/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.memberId")
    public ApiResponse<MemberResponseDto> getMemberApi(@PathVariable("id") Long id) {
        return ApiResponse.ok(MemberResponseDto.from(memberService.getMemberById(id)));
    }
}
