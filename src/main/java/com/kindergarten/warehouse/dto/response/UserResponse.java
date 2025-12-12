package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Set<String> roles;
    private String status;
    private Boolean isDeleted;
}
