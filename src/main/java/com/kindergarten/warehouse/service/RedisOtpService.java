package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OTP service backed by Redis.
 *
 * <p>Handles one-time codes for multiple purposes (email verification, password reset, ...).
 * Uses {@link SecureRandom} and an attempt-limit counter to harden against brute force.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisOtpService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration ATTEMPTS_TTL = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;
    private static final int OTP_BOUND = 1_000_000; // 6-digit uniform distribution

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public enum Purpose {
        EMAIL_VERIFY,
        PASSWORD_RESET,
        /** Flow cũ: admin khởi tạo reset cho user. Giữ để tương thích. */
        ADMIN_PASSWORD_RESET
    }

    /**
     * Tạo OTP mới, lưu vào Redis và reset counter.
     *
     * @return mã OTP 6 chữ số
     */
    public String generateOtp(Purpose purpose, String subject) {
        String otp = String.format("%06d", secureRandom.nextInt(OTP_BOUND));
        redisTemplate.opsForValue().set(key(purpose, subject), otp, DEFAULT_TTL);
        redisTemplate.delete(attemptsKey(purpose, subject));
        return otp;
    }

    /**
     * Validate OTP. Nếu đúng, xóa key (one-time use).
     * Nếu sai, tăng counter; vượt {@value #MAX_ATTEMPTS} sẽ invalidate OTP luôn.
     *
     * @throws AppException {@link ErrorCode#OTP_ATTEMPT_EXCEEDED} nếu đã vượt ngưỡng.
     * @throws AppException {@link ErrorCode#INVALID_OTP} nếu sai hoặc hết hạn.
     */
    public void verifyOtp(Purpose purpose, String subject, String otp) {
        String attemptsKey = attemptsKey(purpose, subject);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, ATTEMPTS_TTL);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            Long ttl = redisTemplate.getExpire(attemptsKey, java.util.concurrent.TimeUnit.SECONDS);
            long retryAfter = ttl == null || ttl < 0 ? ATTEMPTS_TTL.toSeconds() : ttl;
            redisTemplate.delete(key(purpose, subject));
            throw AppException.withRetryAfter(ErrorCode.OTP_ATTEMPT_EXCEEDED, retryAfter);
        }

        String key = key(purpose, subject);
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.toString().equals(otp)) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        redisTemplate.delete(key);
        redisTemplate.delete(attemptsKey);
    }

    private String key(Purpose purpose, String subject) {
        return AppConstants.REDIS_OTP_PREFIX + purpose.name().toLowerCase() + ":" + subject;
    }

    private String attemptsKey(Purpose purpose, String subject) {
        return AppConstants.REDIS_OTP_ATTEMPTS_PREFIX + purpose.name().toLowerCase() + ":" + subject;
    }
}
