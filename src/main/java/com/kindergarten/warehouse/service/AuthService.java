package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.ForgotPasswordRequest;
import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.request.ResendVerificationRequest;
import com.kindergarten.warehouse.dto.request.ResetPasswordRequest;
import com.kindergarten.warehouse.dto.request.VerifyEmailRequest;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.UserMapper;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.CustomUserDetails;
import com.kindergarten.warehouse.security.JwtTokenProvider;
import com.kindergarten.warehouse.service.RedisOtpService.Purpose;
import com.kindergarten.warehouse.util.AppConstants;
import com.kindergarten.warehouse.util.RequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Use-cases xác thực: đăng nhập, đăng ký, logout, quên mật khẩu, xác thực email.
 *
 * <p>Ghi chú kỹ thuật:
 * <ul>
 *   <li>Login có rate-limit theo (email + IP).</li>
 *   <li>Register tạo user ở trạng thái {@link UserStatus#PENDING} và gửi OTP xác thực email.</li>
 *   <li>Quên mật khẩu trả về response đồng nhất (không tiết lộ email tồn tại).</li>
 *   <li>Đổi mật khẩu qua OTP sẽ tăng {@code tokenVersion} để vô hiệu hóa JWT cũ.</li>
 *   <li>Email được gửi trong callback {@link TransactionSynchronization#afterCommit()} để tránh
 *       leak khi DB rollback.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final RedisOtpService otpService;
    private final RateLimitService rateLimitService;
    private final UserDetailsService userDetailsService;
    private final CacheManager cacheManager;

    // ------------------------------------------------------------------ LOGIN

    @Transactional
    public AuthResponseDto login(LoginDto loginDto) {
        String identifier = loginDto.getEmail();
        String ip = RequestUtils.getClientIpAddress();
        String userAgent = RequestUtils.getUserAgent();

        rateLimitService.ensureLoginAllowed(identifier, ip);

        User user = userRepository.findActiveByUsernameOrEmail(identifier)
                .orElseThrow(() -> {
                    rateLimitService.recordFailedLogin(identifier, ip);
                    return new AppException(ErrorCode.UNAUTHENTICATED);
                });

        if (!passwordEncoder.matches(loginDto.getPassword(), user.getPassword())) {
            rateLimitService.recordFailedLogin(identifier, ip);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        assertAccountLoginable(user);

        rateLimitService.reset(identifier, ip);

        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(userDetails);

        auditLogService.saveLog("LOGIN", user.getUsername(), "AuthService",
                "User logged in successfully", ip, userAgent);

        return new AuthResponseDto(userMapper.toResponse(user), token, null);
    }

    // --------------------------------------------------------------- REGISTER

    @Transactional
    public AuthResponseDto register(RegisterDto registerDto) {
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByEmail(registerDto.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        User user = User.builder()
                .username(registerDto.getUsername())
                .email(registerDto.getEmail())
                .password(passwordEncoder.encode(registerDto.getPassword()))
                .fullName(registerDto.getFullName())
                .roles(Collections.singleton(Role.USER))
                .status(UserStatus.PENDING)
                .emailVerified(false)
                .isDeleted(false)
                .build();

        User saved = userRepository.save(user);

        String otp = otpService.generateOtp(Purpose.EMAIL_VERIFY, saved.getEmail());
        String ip = RequestUtils.getClientIpAddress();
        String userAgent = RequestUtils.getUserAgent();

        runAfterCommit(() -> emailService.sendOtpForEmailVerification(saved.getEmail(), otp));

        auditLogService.saveLog("CREATE", saved.getUsername(), "AuthService",
                "User registered, pending email verification", ip, userAgent);

        return new AuthResponseDto(userMapper.toResponse(saved), null, null);
    }

    // ----------------------------------------------------------------- LOGOUT

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Date expiration = jwtTokenProvider.getExpirationDate(token);
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set(
                        AppConstants.REDIS_JWT_BLACKLIST + token,
                        "blacklisted",
                        ttl,
                        TimeUnit.MILLISECONDS);
            }
            String username = jwtTokenProvider.getUsername(token);
            auditLogService.saveLog("LOGOUT", username, "AuthService", "User logged out",
                    RequestUtils.getClientIpAddress(), RequestUtils.getUserAgent());
        } catch (Exception e) {
            // Token đã hết hạn / không hợp lệ → không cần blacklist
            log.debug("Logout with invalid token, skipping blacklist");
        }
    }

    // -------------------------------------------------------- EMAIL VERIFY

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        otpService.verifyOtp(Purpose.EMAIL_VERIFY, user.getEmail(), request.getOtp());

        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        evictUserCache(user);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        Optional<User> opt = userRepository.findByEmail(request.getEmail());
        if (opt.isEmpty()) {
            return; // Im lặng để không leak email tồn tại
        }
        User user = opt.get();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        String otp = otpService.generateOtp(Purpose.EMAIL_VERIFY, user.getEmail());
        runAfterCommit(() -> emailService.sendOtpForEmailVerification(user.getEmail(), otp));
    }

    // ------------------------------------------------------- FORGOT PASSWORD

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> opt = userRepository.findByEmailAndIsDeletedFalse(request.getEmail());
        if (opt.isEmpty()) {
            return; // Response đồng nhất — không tiết lộ email
        }
        User user = opt.get();

        String otp = otpService.generateOtp(Purpose.PASSWORD_RESET, user.getEmail());
        runAfterCommit(() -> emailService.sendOtpForPasswordReset(user.getEmail(), otp));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP));

        otpService.verifyOtp(Purpose.PASSWORD_RESET, user.getEmail(), request.getOtp());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.incrementTokenVersion();
        userRepository.save(user);

        evictUserCache(user);
        auditLogService.saveLog("PASSWORD_RESET", user.getUsername(), "AuthService",
                "User reset password via OTP",
                RequestUtils.getClientIpAddress(), RequestUtils.getUserAgent());
    }

    // ------------------------------------------------------------ INTERNALS

    private void assertAccountLoginable(User user) {
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }
        switch (user.getStatus()) {
            case PENDING -> throw new AppException(ErrorCode.ACCOUNT_PENDING);
            case BLOCKED -> throw new AppException(ErrorCode.ACCOUNT_BLOCKED);
            case INACTIVE -> throw new AppException(ErrorCode.ACCOUNT_BLOCKED);
            case ACTIVE -> { /* ok */ }
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private void evictUserCache(User user) {
        if (cacheManager.getCache("users") != null) {
            cacheManager.getCache("users").evict(user.getUsername());
            cacheManager.getCache("users").evict(user.getEmail());
        }
        // Đồng thời xóa cache token version để các request sau đọc đúng version mới
        redisTemplate.delete(AppConstants.REDIS_TOKEN_VERSION_PREFIX + user.getId());
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
