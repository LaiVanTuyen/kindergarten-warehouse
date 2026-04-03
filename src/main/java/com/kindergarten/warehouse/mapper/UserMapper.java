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
                // Map Set<Role> to Set<String>
                .roles(user.getRoles().stream()
                        .map(Enum::name)
                        .collect(Collectors.toSet()))
                // Map UserStatus to String
                .status(user.getStatus().name())
                .isDeleted(user.getIsDeleted())
                .phoneNumber(user.getPhoneNumber())
                .createdAt(user.getCreatedAt())
                .bio(user.getBio())
                .lastActive(user.getLastActive())
                .build();
    }
}
