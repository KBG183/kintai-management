package com.example.kintai.config;

import com.example.kintai.global.security.CustomAuthenticationSuccessHandler;
import com.example.kintai.global.security.CustomUserDetailsService;
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
        http
                // REST API エンドポイントに対する CSRF 緩和（SPA/Fetch API との連携を考慮）
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**")
                )
                .authorizeHttpRequests(auth -> auth
                        // 静的リソースおよび認証関連エンドポイントの開放
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/login",
                                "/login-process",
                                "/members/sample"
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
                                "/api/attendance/**",
                                "/api/shift/**"
                        ).hasAnyRole("STAFF", "OWNER")
                        // その他の全リクエストは認証必須
                        .anyRequest().authenticated()
                )
                // フォームログイン設定
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login-process")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                // ログアウト設定
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // 認可例外ハンドリング（権限不足時の403画面を防止しログイン画面へ案内）
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/login?denied=true")
                )
                // セッション管理設定
                .sessionManagement(session -> session
                        .maximumSessions(2)
                        .maxSessionsPreventsLogin(false)
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
