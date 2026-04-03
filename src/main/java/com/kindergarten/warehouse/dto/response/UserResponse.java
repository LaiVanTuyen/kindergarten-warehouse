package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Set<String> roles;
    private String status;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private String phoneNumber;
    private String bio;
    private LocalDateTime lastActive;

    public UserResponse(Long id, String username, String fullName, String email, String avatarUrl, Set<String> roles,
            String status, Boolean isDeleted, LocalDateTime createdAt, String phoneNumber, String bio,
            LocalDateTime lastActive) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.roles = roles;
        this.status = status;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.phoneNumber = phoneNumber;
        this.bio = bio;
        this.lastActive = lastActive;
    }

    public static UserResponseBuilder builder() {
        return new UserResponseBuilder();
    }

    public static class UserResponseBuilder {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private String avatarUrl;
        private Set<String> roles;
        private String status;
        private Boolean isDeleted;
        private LocalDateTime createdAt;
        private String phoneNumber;
        private String bio;
        private LocalDateTime lastActive;

        UserResponseBuilder() {
        }

        public UserResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public UserResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserResponseBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserResponseBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserResponseBuilder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public UserResponseBuilder roles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public UserResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public UserResponseBuilder isDeleted(Boolean isDeleted) {
            this.isDeleted = isDeleted;
            return this;
        }

        public UserResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UserResponseBuilder lastActive(LocalDateTime lastActive) {
            this.lastActive = lastActive;
            return this;
        }

        public UserResponseBuilder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public UserResponseBuilder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public UserResponse build() {
            return new UserResponse(id, username, fullName, email, avatarUrl, roles, status, isDeleted, createdAt,
                    phoneNumber, bio, lastActive);
        }
    }
}
