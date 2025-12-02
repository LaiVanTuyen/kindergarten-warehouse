package com.kindergarten.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponseDto {
    private String accessToken;
    private String tokenType = "Bearer";
    private Long id;
    private String username;
    private String fullName;
    private String role;
}
