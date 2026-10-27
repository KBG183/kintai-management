package com.example.kintai.domain.member.mapper;

import com.example.kintai.domain.member.dto.MemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberMapper {

    /**
     * ID로 회원 단건 조회 (샘플 쿼리)
     */
    MemberDto findById(@Param("memberId") Long memberId);

    /**
     * 회원 전체 목록 조회 (샘플 쿼리)
     */
    List<MemberDto> findAll();
}
