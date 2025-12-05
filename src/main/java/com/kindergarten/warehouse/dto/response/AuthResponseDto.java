package com.kindergarten.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponseDto {
    private UserResponse user;
    private String accessToken;
    private String refreshToken;
}
