package com.kindergarten.warehouse.dto;

import com.kindergarten.warehouse.entity.Role;
import lombok.Data;

@Data
public class RegisterDto {
    private String username;
    private String password;
    private String fullName;
    private Role role; // Optional, default to USER if null
}
