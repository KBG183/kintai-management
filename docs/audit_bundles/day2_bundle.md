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
