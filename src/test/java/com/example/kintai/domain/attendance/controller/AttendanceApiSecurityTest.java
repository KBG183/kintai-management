package com.example.kintai.domain.attendance.controller;

import com.example.kintai.domain.attendance.dto.AttendanceModRequestDto;
import com.example.kintai.domain.attendance.dto.AttendanceModReviewDto;
import com.example.kintai.domain.attendance.service.AttendanceService;
import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.exception.GlobalExceptionHandler;
import com.example.kintai.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AttendanceApiSecurityTest（勤怠コントローラー IDOR 多層防御 MockMvc HTTP 検証）
 * 1. スタッフによる他者ID改ざん申請の強制セッションバインド防御（HTTP 200 & 実DTO上書き実証）
 * 2. オーナーによる管轄店舗スタッフ代理申請の正常許可（HTTP 200）
 * 3. オーナーによる他店舗スタッフ代理申請の HTTP 403 Forbidden 遮断
 * 4. オーナーによる管轄店舗の修正申請審査の正常許可（HTTP 200）
 * 5. オーナーによる他店舗修正申請審査の HTTP 403 Forbidden 遮断
 * 6. 未認証ユーザー（userDetails == null）の修正申請に対する HTTP 403 Forbidden 遮断
 * 7. 未認証ユーザー（userDetails == null）の審査処理に対する HTTP 403 Forbidden 遮断
 */
@DisplayName("AttendanceApiController IDOR認可多層防御 MockMvc HTTPテスト")
class AttendanceApiSecurityTest {

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private StoreAuthorizationService storeAuthorizationService;

    @InjectMocks
    private AttendanceApiController attendanceApiController;

    private MockMvc mockMvc;
    private CustomUserDetails currentPrincipal;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // HTTPリクエストから @AuthenticationPrincipal に currentPrincipal を動的インジェクションするリゾルバー
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return currentPrincipal;
            }
        };

        // standaloneSetup + GlobalExceptionHandler（AccessDeniedException -> HTTP 403 Forbidden）
        mockMvc = MockMvcBuilders.standaloneSetup(attendanceApiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    private CustomUserDetails createStaffDetails(Long memberId, Long storeStaffId, Long storeId) {
        MemberDto member = new MemberDto();
        member.setMemberId(memberId);
        member.setEmail("staff@kintai.com");
        member.setName("一般スタッフ");
        member.setRole("ROLE_STAFF");
        member.setIsActive(true);
        return new CustomUserDetails(member, storeStaffId, storeId);
    }

    private CustomUserDetails createOwnerDetails(Long memberId) {
        MemberDto member = new MemberDto();
        member.setMemberId(memberId);
        member.setEmail("owner@kintai.com");
        member.setName("店舗オーナー");
        member.setRole("ROLE_OWNER");
        member.setIsActive(true);
        return new CustomUserDetails(member, null, null);
    }

    @Nested
    @DisplayName("勤怠修正申請（POST /api/attendance/mod-request）のIDOR防御 HTTPテスト")
    class ModRequestSecurityTest {

        @Test
        @DisplayName("スタッフがリクエスト本文で他人のstoreStaffIdを指定してもセッションの本人IDに強制バインドされること（HTTP 200）")
        void requestModification_StaffAttemptsSpoofing_ShouldForceBindSessionStaffId() throws Exception {
            Long myStaffId = 10L;
            Long victimStaffId = 999L; // 他人のスタッフIDを捏造
            Long myStoreId = 1L;

            String jsonPayload = """
                {
                    "storeStaffId": 999,
                    "storeId": 1,
                    "workDate": "2026-11-10",
                    "requestedClockInAt": "2026-11-10T09:00:00",
                    "requestedClockOutAt": "2026-11-10T18:00:00",
                    "reason": "打刻忘れ修正"
                }
                """;

            when(attendanceService.requestModification(any(AttendanceModRequestDto.class))).thenReturn(100L);
            currentPrincipal = createStaffDetails(1L, myStaffId, myStoreId);

            mockMvc.perform(post("/api/attendance/mod-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(100L));

            // 検証：サービスへ渡されたDTOの storeStaffId が本人ID(10L)に強制上書きされていること
            ArgumentCaptor<AttendanceModRequestDto> captor = ArgumentCaptor.forClass(AttendanceModRequestDto.class);
            verify(attendanceService).requestModification(captor.capture());
            assertThat(captor.getValue().getStoreStaffId()).isEqualTo(myStaffId);
            verify(storeAuthorizationService, never()).validateStaffStoreOwnership(any(), any());
        }

        @Test
        @DisplayName("オーナーが自己所有店舗のスタッフの修正を代理申請する場合は正常に許可されること（HTTP 200）")
        void requestModification_OwnerForOwnStaff_ShouldSucceed() throws Exception {
            Long ownerMemberId = 2L;
            Long targetStaffId = 10L;

            String jsonPayload = """
                {
                    "storeStaffId": 10,
                    "workDate": "2026-11-10",
                    "requestedClockInAt": "2026-11-10T09:00:00",
                    "requestedClockOutAt": "2026-11-10T18:00:00",
                    "reason": "オーナー代理申請"
                }
                """;

            when(attendanceService.requestModification(any(AttendanceModRequestDto.class))).thenReturn(101L);
            currentPrincipal = createOwnerDetails(ownerMemberId);

            mockMvc.perform(post("/api/attendance/mod-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(101L));

            // 検証：オーナーシップ検証が実行されたこと
            verify(storeAuthorizationService).validateStaffStoreOwnership(targetStaffId, ownerMemberId);
            verify(attendanceService).requestModification(any(AttendanceModRequestDto.class));
        }

        @Test
        @DisplayName("オーナーが他人の店舗に所属するスタッフの代理申請を試みた場合は HTTP 403 Forbidden で遮断されること")
        void requestModification_OwnerForOtherStoreStaff_ShouldReturn403Forbidden() throws Exception {
            Long ownerMemberId = 2L;
            Long attackerTargetStaffId = 777L;

            String jsonPayload = """
                {
                    "storeStaffId": 777,
                    "workDate": "2026-11-10",
                    "requestedClockInAt": "2026-11-10T09:00:00",
                    "requestedClockOutAt": "2026-11-10T18:00:00",
                    "reason": "不正代理申請"
                }
                """;

            doThrow(new AccessDeniedException("対象スタッフに対する管理権限がありません。"))
                    .when(storeAuthorizationService).validateStaffStoreOwnership(attackerTargetStaffId, ownerMemberId);

            currentPrincipal = createOwnerDetails(ownerMemberId);

            mockMvc.perform(post("/api/attendance/mod-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("対象スタッフに対する管理権限がありません。"));

            verify(attendanceService, never()).requestModification(any());
        }
    }

    @Nested
    @DisplayName("勤怠修正申請の審査（POST /api/attendance/mod-requests/{id}/review）のIDOR防御 HTTPテスト")
    class ReviewModificationSecurityTest {

        @Test
        @DisplayName("オーナーが自己所有店舗の勤怠修正申請を審査する場合は正常に成功すること（HTTP 200）")
        void reviewModification_OwnerForOwnStoreModRequest_ShouldSucceed() throws Exception {
            Long ownerMemberId = 2L;
            Long modRequestId = 50L;

            String jsonPayload = """
                {
                    "action": "APPROVE",
                    "rejectReason": null
                }
                """;

            currentPrincipal = createOwnerDetails(ownerMemberId);

            mockMvc.perform(post("/api/attendance/mod-requests/{modRequestId}/review", modRequestId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 検証：修正申請所有権検証およびサービス呼び出しが実行されたこと
            verify(storeAuthorizationService).validateModRequestStoreOwnership(modRequestId, ownerMemberId);
            verify(attendanceService).reviewModification(eq(modRequestId), eq(ownerMemberId), any(AttendanceModReviewDto.class));
        }

        @Test
        @DisplayName("オーナーが他人の店舗の修正申請IDを不正審査しようとした場合は HTTP 403 Forbidden で遮断されること")
        void reviewModification_OwnerForOtherStoreModRequest_ShouldReturn403Forbidden() throws Exception {
            Long ownerMemberId = 2L;
            Long targetModRequestId = 999L; // 他店舗の申請ID

            String jsonPayload = """
                {
                    "action": "APPROVE",
                    "rejectReason": null
                }
                """;

            doThrow(new AccessDeniedException("対象勤怠修正申請に対する審査権限がありません。"))
                    .when(storeAuthorizationService).validateModRequestStoreOwnership(targetModRequestId, ownerMemberId);

            currentPrincipal = createOwnerDetails(ownerMemberId);

            mockMvc.perform(post("/api/attendance/mod-requests/{modRequestId}/review", targetModRequestId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("対象勤怠修正申請に対する審査権限がありません。"));

            verify(attendanceService, never()).reviewModification(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("未認証アクセスの多層防御 HTTPテスト")
    class UnauthenticatedSecurityTest {

        @Test
        @DisplayName("未認証状態（currentPrincipal == null）で修正申請を試みた場合は HTTP 403 Forbidden で遮断されること")
        void requestModification_Unauthenticated_ShouldReturn403Forbidden() throws Exception {
            String jsonPayload = """
                {
                    "storeStaffId": 1,
                    "workDate": "2026-11-10",
                    "requestedClockInAt": "2026-11-10T09:00:00",
                    "requestedClockOutAt": "2026-11-10T18:00:00",
                    "reason": "未認証申請"
                }
                """;

            currentPrincipal = null; // 未認証

            mockMvc.perform(post("/api/attendance/mod-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("勤怠修正申請には認証が必要です。"));

            verify(attendanceService, never()).requestModification(any());
        }

        @Test
        @DisplayName("未認証状態（currentPrincipal == null）で審査処理を試みた場合は HTTP 403 Forbidden で遮断されること")
        void reviewModification_Unauthenticated_ShouldReturn403Forbidden() throws Exception {
            String jsonPayload = """
                {
                    "action": "APPROVE"
                }
                """;

            currentPrincipal = null; // 未認証

            mockMvc.perform(post("/api/attendance/mod-requests/1/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("審査処理には認証が必要です。"));

            verify(attendanceService, never()).reviewModification(any(), any(), any());
        }
    }
}
