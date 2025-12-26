package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Set<String> roles;
    private String status;
    private Boolean isDeleted;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String fullName, String email, String avatarUrl, Set<String> roles,
            String status, Boolean isDeleted) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.roles = roles;
        this.status = status;
        this.isDeleted = isDeleted;
    }

    public static UserResponseBuilder builder() {
        return new UserResponseBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
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

        public UserResponse build() {
            return new UserResponse(id, username, fullName, email, avatarUrl, roles, status, isDeleted);
        }
    }
}
