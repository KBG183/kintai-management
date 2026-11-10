package com.example.kintai.domain.member.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * クライアント返却専用の会員情報レスポンスDTO。
 * パスワードハッシュ等の機密情報を完全に排除し、安全な情報開示を保証する。
 */
@Getter
@Builder
@ToString
public class MemberResponseDto {
    private Long memberId;
    private String email;
    private String name;
    private String phone;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MemberResponseDto from(MemberDto dto) {
        if (dto == null) {
            return null;
        }
        return MemberResponseDto.builder()
                .memberId(dto.getMemberId())
                .email(dto.getEmail())
                .name(dto.getName())
                .phone(dto.getPhone())
                .role(dto.getRole())
                .isActive(dto.getIsActive())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
