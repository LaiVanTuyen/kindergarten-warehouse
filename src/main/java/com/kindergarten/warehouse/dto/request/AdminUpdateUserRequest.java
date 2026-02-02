package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.UserStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AdminUpdateUserRequest {
    private String fullName;
    private String phoneNumber;
    private String bio;
    private Set<String> roles;
    private UserStatus status;
}
