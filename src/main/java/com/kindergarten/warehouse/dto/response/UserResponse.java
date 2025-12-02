package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private Role role;
    private Boolean isActive;
}
