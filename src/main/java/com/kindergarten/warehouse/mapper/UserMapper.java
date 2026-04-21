package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.entity.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        // Debug Log Removed

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .roles(user.getRoles() == null
                        ? java.util.Set.of()
                        : user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .status(user.getStatus() == null ? null : user.getStatus().name())
                .isDeleted(user.getIsDeleted())
                .emailVerified(user.getEmailVerified())
                .blockedReason(user.getBlockedReason())
                .blockedAt(user.getBlockedAt())
                .phoneNumber(user.getPhoneNumber())
                .createdAt(user.getCreatedAt())
                .bio(user.getBio())
                .lastActive(user.getLastActive())
                .build();
    }
}
