package com.example.kintai.global.security;

import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.domain.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security ユーザー詳細取得サービスクラス
 * メールアドレスをキーとして会員情報および所属店舗・スタッフ属性を取得
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        MemberDto member = memberMapper.findByEmail(email);
        if (member == null) {
            throw new UsernameNotFoundException("該当するメールアドレスのアカウントが存在しません: " + email);
        }

        if (Boolean.FALSE.equals(member.getIsActive())) {
            throw new UsernameNotFoundException("無効化されたアカウントです: " + email);
        }

        Long storeStaffId = null;
        Long storeId = null;

        if ("ROLE_STAFF".equals(member.getRole())) {
            StaffStoreInfoDto staffInfo = memberMapper.findStaffStoreInfoByMemberId(member.getMemberId());
            if (staffInfo != null) {
                storeStaffId = staffInfo.getStoreStaffId();
                storeId = staffInfo.getStoreId();
            }
        } else if ("ROLE_OWNER".equals(member.getRole())) {
            storeId = memberMapper.findStoreIdByOwnerMemberId(member.getMemberId());
        }

        return new CustomUserDetails(member, storeStaffId, storeId);
    }
}
