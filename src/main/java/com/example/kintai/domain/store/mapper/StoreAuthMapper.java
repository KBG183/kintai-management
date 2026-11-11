package com.example.kintai.domain.store.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 店舗リソース・勤怠修正申請・給与データの所有権検証用 MyBatis Mapper
 */
@Mapper
public interface StoreAuthMapper {

    /**
     * 店舗IDからオーナー（会員ID）を取得
     */
    @Select("SELECT owner_id FROM store WHERE store_id = #{storeId}")
    Long findOwnerIdByStoreId(@Param("storeId") Long storeId);

    /**
     * 店舗スタッフIDから所属店舗のオーナー（会員ID）を取得
     */
    @Select("SELECT s.owner_id FROM store s JOIN store_staff ss ON s.store_id = ss.store_id WHERE ss.store_staff_id = #{storeStaffId}")
    Long findOwnerIdByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

    /**
     * 店舗スタッフIDから所属店舗ID（store_id）を取得（テナント偽装防止用）
     */
    @Select("SELECT store_id FROM store_staff WHERE store_staff_id = #{storeStaffId}")
    Long findStoreIdByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

    /**
     * 給与IDから対象店舗のオーナー（会員ID）を取得
     */
    @Select("SELECT s.owner_id FROM store s JOIN payroll p ON s.store_id = p.store_id WHERE p.payroll_id = #{payrollId}")
    Long findOwnerIdByPayrollId(@Param("payrollId") Long payrollId);

    /**
     * 勤怠修正申請IDから対象店舗のオーナー（会員ID）を取得
     */
    @Select("SELECT s.owner_id FROM store s JOIN attendance_mod_request amr ON s.store_id = amr.store_id WHERE amr.mod_request_id = #{modRequestId}")
    Long findOwnerIdByModRequestId(@Param("modRequestId") Long modRequestId);
}
