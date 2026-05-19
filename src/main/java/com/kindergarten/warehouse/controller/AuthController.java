package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.ForgotPasswordRequest;
import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.request.ResendVerificationRequest;
import com.kindergarten.warehouse.dto.request.ResetPasswordRequest;
import com.kindergarten.warehouse.dto.request.VerifyEmailRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.service.AuthService;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.util.AppConstants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

    private static final int COOKIE_MAX_AGE_SECONDS = 24 * 60 * 60;

    private final AuthService authService;
    private final MessageService messageService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site}")
    private String cookieSameSite;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@Valid @RequestBody LoginDto loginDto) {
        AuthResponseDto response = authService.login(loginDto);
        String accessToken = response.getAccessToken();
        response.setAccessToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(accessToken).toString())
                .body(ApiResponse.success(response, messageService.getMessage("auth.login.success")));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDto>> register(@Valid @RequestBody RegisterDto registerDto) {
        AuthResponseDto response = authService.register(registerDto);
        // Register KHÔNG auto-login: user phải verify email trước
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, messageService.getMessage("auth.register.success")));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String token = extractTokenFromCookie(request);
        authService.logout(token);

        ResponseCookie expiredCookie = ResponseCookie.from(AppConstants.COOKIE_ACCESS_TOKEN, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.success(null, messageService.getMessage("auth.logout.success")));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, messageService.getMessage("auth.password.forgot.initiated")));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, messageService.getMessage("auth.password.reset.success")));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, messageService.getMessage("auth.email.verify.success")));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, messageService.getMessage("auth.email.verify.resent")));
    }

    private ResponseCookie buildAccessTokenCookie(String accessToken) {
        return ResponseCookie.from(AppConstants.COOKIE_ACCESS_TOKEN, accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .sameSite(cookieSameSite)
                .build();
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (AppConstants.COOKIE_ACCESS_TOKEN.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
