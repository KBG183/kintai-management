package com.example.kintai.domain.store.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 店舗リソースへのアクセス権限およびオーナーシップ（所有権）を明示的に検証するセキュリティサービス。
 * AOPによるブラックボックス化を排除し、サービス層で確実に呼び出すことで監査性とIDOR耐性を担保する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreAuthorizationService {

    @Mapper
    public interface StoreAuthMapper {
        @Select("SELECT owner_id FROM store WHERE store_id = #{storeId}")
        Long findOwnerIdByStoreId(@Param("storeId") Long storeId);

        @Select("SELECT s.owner_id FROM store s JOIN store_staff ss ON s.store_id = ss.store_id WHERE ss.store_staff_id = #{storeStaffId}")
        Long findOwnerIdByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

        @Select("SELECT s.owner_id FROM store s JOIN payroll p ON s.store_id = p.store_id WHERE p.payroll_id = #{payrollId}")
        Long findOwnerIdByPayrollId(@Param("payrollId") Long payrollId);
    }

    private final StoreAuthMapper storeAuthMapper;

    /**
     * 現在ログイン中のユーザーが対象店舗のオーナーであるかを検証
     *
     * @param storeId 対象店舗ID
     * @param currentMemberId 認証中ユーザーの会員ID
     * @throws AccessDeniedException 所有権が存在しない場合
     */
    public void validateStoreOwnership(Long storeId, Long currentMemberId) {
        if (storeId == null || currentMemberId == null) {
            throw new AccessDeniedException("店舗IDまたは認証情報が不正です。");
        }
        Long ownerId = storeAuthMapper.findOwnerIdByStoreId(storeId);
        if (ownerId == null || !ownerId.equals(currentMemberId)) {
            log.warn("IDOR検知: 店舗所有権不一致 storeId={}, currentMemberId={}, actualOwnerId={}",
                    storeId, currentMemberId, ownerId);
            throw new AccessDeniedException("対象店舗の管理権限がありません。");
        }
    }

    /**
     * 給与IDに基づく店舗オーナーシップ検証
     */
    public void validatePayrollOwnership(Long payrollId, Long currentMemberId) {
        if (payrollId == null || currentMemberId == null) {
            throw new AccessDeniedException("給与IDまたは認証情報が不正です。");
        }
        Long ownerId = storeAuthMapper.findOwnerIdByPayrollId(payrollId);
        if (ownerId == null || !ownerId.equals(currentMemberId)) {
            log.warn("IDOR検知: 給与データ所有権不一致 payrollId={}, currentMemberId={}, actualOwnerId={}",
                    payrollId, currentMemberId, ownerId);
            throw new AccessDeniedException("対象給与データの管理権限がありません。");
        }
    }

    /**
     * 店舗スタッフIDに基づく店舗オーナーシップ検証
     */
    public void validateStaffStoreOwnership(Long storeStaffId, Long currentMemberId) {
        if (storeStaffId == null || currentMemberId == null) {
            throw new AccessDeniedException("スタッフIDまたは認証情報が不正です。");
        }
        Long ownerId = storeAuthMapper.findOwnerIdByStoreStaffId(storeStaffId);
        if (ownerId == null || !ownerId.equals(currentMemberId)) {
            log.warn("IDOR検知: スタッフ所属店舗所有権不一致 storeStaffId={}, currentMemberId={}, actualOwnerId={}",
                    storeStaffId, currentMemberId, ownerId);
            throw new AccessDeniedException("対象スタッフに対する管理権限がありません。");
        }
    }
}
