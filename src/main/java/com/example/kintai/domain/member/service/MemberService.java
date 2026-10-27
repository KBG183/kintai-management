package com.example.kintai.domain.member.service;

import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.domain.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberDto getMemberById(Long memberId) {
        MemberDto member = memberMapper.findById(memberId);
        if (member == null) {
            throw new IllegalArgumentException("해당 회원을 찾을 수 없습니다. (ID: " + memberId + ")");
        }
        return member;
    }

    public List<MemberDto> getAllMembers() {
        return memberMapper.findAll();
    }
}
