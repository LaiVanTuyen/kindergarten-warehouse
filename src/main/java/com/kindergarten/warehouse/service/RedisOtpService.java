package com.kindergarten.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisOtpService {

    private final RedisTemplate<String, Object> redisTemplate;

    // OTP valid for 5 minutes
    private static final long OTP_TTL_MINUTES = 5;
    private static final String OTP_PREFIX = "otp:reset:";

    /**
     * Generate a 6-digit OTP and store in Redis
     */
    public String generateOtp(Long userId) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        String key = OTP_PREFIX + userId;

        redisTemplate.opsForValue().set(key, otp, Duration.ofMinutes(OTP_TTL_MINUTES));

        log.info("Generated OTP for userId {}: {}", userId, otp); // Log for Debug/Dev mode
        return otp;
    }

    /**
     * Validate OTP. If valid, delete it (One-time use)
     */
    public boolean validateOtp(Long userId, String otp) {
        String key = OTP_PREFIX + userId;
        Object storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp != null && storedOtp.toString().equals(otp)) {
            redisTemplate.delete(key); // Invalidate immediately after use
            return true;
        }
        return false;
    }
}
