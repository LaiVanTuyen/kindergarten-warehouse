package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.service.AuthService;
import com.kindergarten.warehouse.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

        private final AuthService authService;
        private final MessageService messageService;

        @Value("${app.cookie.secure}")
        private boolean isCookieSecure;

        @Value("${app.cookie.same-site}")
        private String cookieSameSite;

        @PostMapping("/login")
        public ResponseEntity<ApiResponse<AuthResponseDto>> login(
                        @RequestBody @Valid LoginDto loginDto) {
                AuthResponseDto response = authService.login(loginDto);
                String accessToken = response.getAccessToken();

                // Clear token from response body to force usage of cookie
                response.setAccessToken(null);

                ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
                                .httpOnly(true)
                                .secure(isCookieSecure)
                                .path("/")
                                .maxAge(24 * 60 * 60) // 1 day
                                .sameSite(cookieSameSite)
                                .build();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(ApiResponse.success(response,
                                                messageService.getMessage("auth.login.success")));
        }

        @PostMapping("/register")
        public ResponseEntity<ApiResponse<AuthResponseDto>> register(
                        @RequestBody @Valid RegisterDto registerDto) {
                AuthResponseDto response = authService.register(registerDto);

                String accessToken = response.getAccessToken();
                response.setAccessToken(null);

                ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
                                .httpOnly(true)
                                .secure(isCookieSecure)
                                .path("/")
                                .maxAge(24 * 60 * 60)
                                .sameSite(cookieSameSite)
                                .build();

                return ResponseEntity.status(HttpStatus.CREATED)
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(ApiResponse.success(response,
                                                messageService.getMessage("auth.register.success")));
        }
}
