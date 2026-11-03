package com.example.kintai.global.security;

import com.example.kintai.domain.member.dto.MemberDto;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 認証プリンシパル実装クラス
 * セッション内で認証済みユーザーの属性（会員ID、権限、所属店舗・スタッフID）を保持
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long memberId;
    private final String email;
    private final String password;
    private final String name;
    private final String role;
    private final boolean isActive;
    private final Long storeStaffId;
    private final Long storeId;

    public CustomUserDetails(MemberDto member, Long storeStaffId, Long storeId) {
        this.memberId = member.getMemberId();
        this.email = member.getEmail();
        this.password = member.getPassword();
        this.name = member.getName();
        this.role = member.getRole();
        this.isActive = Boolean.TRUE.equals(member.getIsActive());
        this.storeStaffId = storeStaffId;
        this.storeId = storeId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(this.role));
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.isActive;
    }
}
