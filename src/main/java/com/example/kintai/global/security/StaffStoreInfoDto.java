package com.example.kintai.global.security;

import lombok.Getter;
import lombok.Setter;

/**
 * スタッフの所属店舗およびスタッフ識別情報DTO
 */
@Getter
@Setter
public class StaffStoreInfoDto {
    private Long storeStaffId;
    private Long storeId;
}
