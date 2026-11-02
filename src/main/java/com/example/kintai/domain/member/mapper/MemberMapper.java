package com.example.kintai.domain.member.mapper;

import com.example.kintai.domain.member.dto.MemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberMapper {

    /**
     * 会員IDによる単一会員取得
     */
    MemberDto findById(@Param("memberId") Long memberId);

    /**
     * 会員全件取得
     */
    List<MemberDto> findAll();
}
