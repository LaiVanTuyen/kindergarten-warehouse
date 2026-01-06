package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kindergarten.warehouse.dto.response.ApiResponse;

import com.kindergarten.warehouse.service.MessageService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

        private final AuthService authService;
        private final MessageService messageService;

        @Value("${app.cookie.secure}")
        private boolean isCookieSecure;

        @Value("${app.cookie.same-site}")
        private String cookieSameSite;

        public AuthController(AuthService authService, MessageService messageService) {
                this.authService = authService;
                this.messageService = messageService;
        }

        @PostMapping("/login")
        public ResponseEntity<ApiResponse<AuthResponseDto>> login(
                        @RequestBody @jakarta.validation.Valid LoginDto loginDto) {
                AuthResponseDto response = authService.login(loginDto);
                String accessToken = response.getAccessToken(); // Assume DTO still has it, but we won't return
                                                                // it in body

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
                        @RequestBody @jakarta.validation.Valid RegisterDto registerDto) {
                AuthResponseDto response = authService.register(registerDto);
                // Initially register might not auto-login or set cookie, or we can choose to.
                // For now, let's keep it as is, or we should set cookie here too if it logs
                // them in.
                // Assuming register just creates user. If it returns token, we should probably
                // treat it like login.
                // Let's assume for now register DOES return a token (based on response type).
                // Ideally we should do same cookie logic here if we want auto-login.

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
