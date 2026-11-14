# Final Full Audit Bundle (Re-audit after @EnableMethodSecurity): bundle_1_auth_security_store
Generated: 2026-11-14T14:08:56+09:00

## FILE: src\main\java\com\example\kintai\config\SecurityConfig.java
```
package com.example.kintai.config;

import com.example.kintai.global.security.CustomAuthenticationSuccessHandler;
import com.example.kintai.global.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Spring Security 6 セキュリティ設定クラス
 * セッションベースの認証・権限管理およびアクセス制御を統合設定
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        SpaCsrfTokenRequestHandler requestHandler = new SpaCsrfTokenRequestHandler();

        http
                // BREACH攻撃防御とSPA/Fetch（Cookie/Header）の互換性を両立するCSRF多層防御設定
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 静的リソースおよび認証関連エンドポイントの開放
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/login",
                                "/login-process"
                        ).permitAll()
                        // オーナー専用エンドポイント（給与計算、勤怠修正承認等）
                        .requestMatchers(
                                "/payroll/**",
                                "/api/payroll/**",
                                "/attendance/mod-requests/**",
                                "/api/attendance/mod-requests/**"
                        ).hasRole("OWNER")
                        // 勤怠打刻およびシフト管理（スタッフ・オーナー共通）
                        .requestMatchers(
                                "/attendance",
                                "/attendance/**",
                                "/shift/**",
                                "/shifts",
                                "/shifts/**",
                                "/api/shifts/**",
                                "/api/attendance/**"
                        ).hasAnyRole("STAFF", "OWNER", "ADMIN")
                        // サンプル確認用画面
                        .requestMatchers(
                                "/members/sample",
                                "/api/members/**"
                        ).hasRole("ADMIN")
                        // その他のリクエストは認証必須
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login-process")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return authBuilder.build();
    }

    /**
     * SPA/Fetch API向けCSRFトークンリクエストハンドラー
     * ヘッダー経由（Cookieの生トークン）は平文比較、フォーム送信はBREACH防御（XOR）を自動判別
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get(); // クッキーを確実に発行させるため遅延ロードを即時解決
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue))
                    ? this.plain.resolveCsrfTokenValue(request, csrfToken)
                    : this.xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /**
     * レスポンスヘッダーおよびCookieに最新のCSRFトークンを確実に反映させるフィルター
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // クッキーの書き出しをトリガー
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
```

## FILE: src\main\java\com\example\kintai\global\security\CustomAuthenticationSuccessHandler.java
```
package com.example.kintai.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * ログイン成功時のリダイレクトハンドラー
 * ユーザー権限（ROLE_OWNER / ROLE_STAFF）に応じて初期遷移先を動的に制御
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        boolean isOwner = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OWNER"::equals);

        if (isOwner) {
            response.sendRedirect("/attendance/mod-requests");
        } else {
            response.sendRedirect("/attendance");
        }
    }
}
```

## FILE: src\main\java\com\example\kintai\global\security\CustomUserDetails.java
```
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
```

## FILE: src\main\java\com\example\kintai\global\security\CustomUserDetailsService.java
```
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
```

## FILE: src\main\java\com\example\kintai\domain\auth\controller\AuthController.java
```
package com.example.kintai.domain.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 認証・認可関連 画面遷移コントローラー
 */
@Controller
public class AuthController {

    /**
     * ログイン画面表示
     */
    @GetMapping("/login")
    public String loginView(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "denied", required = false) String denied,
            Authentication authentication,
            Model model
    ) {
        // 既に認証済みの場合はロールに応じたダッシュボードへリダイレクト
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            boolean isOwner = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
            return isOwner ? "redirect:/attendance/mod-requests" : "redirect:/attendance";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "メールアドレスまたはパスワードが正しくありません。");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "正常にログアウトしました。");
        }
        if (denied != null) {
            model.addAttribute("errorMessage", "アクセス権限がありません。管理者アカウントでログインしてください。");
        }

        return "auth/login";
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\member\controller\MemberController.java
```
package com.example.kintai.domain.member.controller;

import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.domain.member.dto.MemberResponseDto;
import com.example.kintai.domain.member.service.MemberService;
import com.example.kintai.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 【画面サンプル】Thymeleaf共通レイアウトおよびデータバインディング確認
     * URL: GET /members/sample (ADMIN専用に保護)
     */
    @GetMapping("/members/sample")
    @PreAuthorize("hasRole('ADMIN')")
    public String memberSampleView(Model model) {
        List<MemberResponseDto> members = memberService.getAllMembers().stream()
                .map(MemberResponseDto::from)
                .toList();
        model.addAttribute("members", members);
        return "member/sample";
    }

    /**
     * 【REST API】全スタッフ・オーナー一覧取得 (JSON, ADMIN専用)
     * URL: GET /api/members
     */
    @GetMapping("/api/members")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MemberResponseDto>> getAllMembersApi() {
        List<MemberResponseDto> responses = memberService.getAllMembers().stream()
                .map(MemberResponseDto::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    /**
     * 【REST API】単一会員情報取得 (ADMINまたは本人専用)
     * URL: GET /api/members/{id}
     */
    @GetMapping("/api/members/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.memberId")
    public ApiResponse<MemberResponseDto> getMemberApi(@PathVariable("id") Long id) {
        return ApiResponse.ok(MemberResponseDto.from(memberService.getMemberById(id)));
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\member\service\MemberService.java
```
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
            throw new IllegalArgumentException("該当する会員が見つかりません。(ID: " + memberId + ")");
        }
        return member;
    }

    public List<MemberDto> getAllMembers() {
        return memberMapper.findAll();
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\member\mapper\MemberMapper.java
```
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
```

## FILE: src\main\resources\mapper\member\MemberMapper.xml
```
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.member.mapper.MemberMapper">

    <!-- 会員単件取得 -->
    <select id="findById" resultType="com.example.kintai.domain.member.dto.MemberDto">
        SELECT
            member_id,
            email,
            password,
            name,
            phone,
            role,
            is_active,
            created_at,
            updated_at
        FROM member
        WHERE member_id = #{memberId}
    </select>

    <!-- メールアドレスによる会員取得（認証用） -->
    <select id="findByEmail" resultType="com.example.kintai.domain.member.dto.MemberDto">
        SELECT
            member_id,
            email,
            password,
            name,
            phone,
            role,
            is_active,
            created_at,
            updated_at
        FROM member
        WHERE email = #{email}
    </select>

    <!-- 会員全件一覧取得 -->
    <select id="findAll" resultType="com.example.kintai.domain.member.dto.MemberDto">
        SELECT
            member_id,
            email,
            password,
            name,
            phone,
            role,
            is_active,
            created_at,
            updated_at
        FROM member
        ORDER BY member_id DESC
    </select>

    <!-- 会員IDに紐づく有効な店舗スタッフID取得 -->
    <select id="findStoreStaffIdByMemberId" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE member_id = #{memberId}
          AND employment_status = 'ACTIVE'
        LIMIT 1
    </select>

    <!-- 会員IDに紐づく所属店舗・スタッフ識別情報取得 -->
    <select id="findStaffStoreInfoByMemberId" resultType="com.example.kintai.global.security.StaffStoreInfoDto">
        SELECT
            store_staff_id AS storeStaffId,
            store_id       AS storeId
        FROM store_staff
        WHERE member_id = #{memberId}
          AND employment_status = 'ACTIVE'
        LIMIT 1
    </select>

    <!-- オーナー会員IDに紐づく代表店舗ID取得 -->
    <select id="findStoreIdByOwnerMemberId" resultType="java.lang.Long">
        SELECT store_id
        FROM store
        WHERE owner_id = #{memberId}
        LIMIT 1
    </select>

</mapper>
```

## FILE: src\main\java\com\example\kintai\domain\store\service\StoreAuthorizationService.java
```
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
```

## FILE: src\main\java\com\example\kintai\domain\store\mapper\StoreAuthMapper.java
```
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
```

## FILE: src\main\java\com\example\kintai\global\exception\GlobalExceptionHandler.java
```
package com.example.kintai.global.exception;

import com.example.kintai.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 入力値バリデーション (@Valid) 失敗時のハンドリング
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(errorMessage));
    }

    // ビジネスロジック例外 (IllegalArgumentException / IllegalStateException) のハンドリング
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    // 認可・権限不一致例外 (AccessDeniedException) のハンドリング（HTTP 403 Forbidden）
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("アクセス拒絶 (AccessDenied): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    // その他サーバー内部例外のハンドリング（SQLやスタックトレースの隠蔽およびロギング）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) throws Exception {
        // HTML画面リクエストの場合はデフォルトのエラーページへ委譲
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html") && !request.getRequestURI().startsWith("/api/")) {
            throw ex;
        }
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("サーバー内部エラーが発生しました。しばらくしてから再度お試しください。"));
    }
}
```

