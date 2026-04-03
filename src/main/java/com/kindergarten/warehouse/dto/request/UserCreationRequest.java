package com.kindergarten.warehouse.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserCreationRequest extends RegisterDto {
    // Optional: Assign roles upon creation (if admin allows)
    private Set<String> roles;
}
