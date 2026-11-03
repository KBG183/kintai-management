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
     * メールアドレスによる単一会員取得（認証用）
     */
    MemberDto findByEmail(@Param("email") String email);

    /**
     * 会員全件取得
     */
    List<MemberDto> findAll();

    /**
     * 会員IDに紐づく店舗スタッフID（store_staff_id）取得
     */
    Long findStoreStaffIdByMemberId(@Param("memberId") Long memberId);

    /**
     * 会員IDに紐づく所属店舗・スタッフ識別情報（store_id, store_staff_id）取得
     */
    com.example.kintai.global.security.StaffStoreInfoDto findStaffStoreInfoByMemberId(@Param("memberId") Long memberId);

    /**
     * オーナー会員IDに紐づく代表店舗ID（store_id）取得
     */
    Long findStoreIdByOwnerMemberId(@Param("memberId") Long memberId);
}
