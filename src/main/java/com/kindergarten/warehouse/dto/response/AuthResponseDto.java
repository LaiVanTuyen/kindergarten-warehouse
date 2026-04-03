package com.kindergarten.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

public class AuthResponseDto {
    private UserResponse user;
    private String accessToken;
    private String refreshToken;

    public AuthResponseDto() {
    }

    public AuthResponseDto(UserResponse user, String accessToken, String refreshToken) {
        this.user = user;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
