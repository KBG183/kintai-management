package com.example.kintai.domain.store.service;

import com.example.kintai.domain.store.mapper.StoreAuthMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 店舗スタッフIDに基づく店舗オーナーシップ検証および所属店舗ID（storeId）の安全な解決
     *
     * @param storeStaffId 対象店舗スタッフID
     * @param currentMemberId 認証中ユーザーの会員ID
     * @return 検証済みの所属店舗ID
     */
    public Long validateStaffStoreOwnership(Long storeStaffId, Long currentMemberId) {
        if (storeStaffId == null || currentMemberId == null) {
            throw new AccessDeniedException("スタッフIDまたは認証情報が不正です。");
        }
        Long ownerId = storeAuthMapper.findOwnerIdByStoreStaffId(storeStaffId);
        if (ownerId == null || !ownerId.equals(currentMemberId)) {
            log.warn("IDOR検知: スタッフ所属店舗所有権不一致 storeStaffId={}, currentMemberId={}, actualOwnerId={}",
                    storeStaffId, currentMemberId, ownerId);
            throw new AccessDeniedException("対象スタッフに対する管理権限がありません。");
        }
        return storeAuthMapper.findStoreIdByStoreStaffId(storeStaffId);
    }

    /**
     * 勤怠修正申請IDに基づく店舗オーナーシップ検証
     */
    public void validateModRequestStoreOwnership(Long modRequestId, Long currentMemberId) {
        if (modRequestId == null || currentMemberId == null) {
            throw new AccessDeniedException("修正申請IDまたは認証情報が不正です。");
        }
        Long ownerId = storeAuthMapper.findOwnerIdByModRequestId(modRequestId);
        if (ownerId == null || !ownerId.equals(currentMemberId)) {
            log.warn("IDOR検知: 勤怠修正申請所有権不一致 modRequestId={}, currentMemberId={}, actualOwnerId={}",
                    modRequestId, currentMemberId, ownerId);
            throw new AccessDeniedException("対象勤怠修正申請に対する審査権限がありません。");
        }
    }
}
