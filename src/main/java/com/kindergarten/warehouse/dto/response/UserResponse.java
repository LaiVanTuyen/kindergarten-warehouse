package com.kindergarten.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Set<String> roles;
    private String status;
    private Boolean isDeleted;
    private Boolean emailVerified;
    private String blockedReason;
    private LocalDateTime blockedAt;
    private LocalDateTime createdAt;
    private String phoneNumber;
    private String bio;
    private LocalDateTime lastActive;
}
