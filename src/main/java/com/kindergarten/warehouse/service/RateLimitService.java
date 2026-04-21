package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Giới hạn số lần login sai theo cặp (identifier + IP) trong một cửa sổ thời gian.
 *
 * <p>Đếm tăng mỗi lần sai, TTL = lockout window. Vượt ngưỡng → ném AppException.
 * Khi login thành công → reset counter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    public void ensureLoginAllowed(String identifier, String ipAddress) {
        String key = buildKey(identifier, ipAddress);
        Object v = redisTemplate.opsForValue().get(key);
        long attempts = toLong(v);
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
            long retryAfter = ttl == null || ttl < 0 ? LOGIN_LOCKOUT.toSeconds() : ttl;
            throw AppException.withRetryAfter(ErrorCode.LOGIN_ATTEMPT_EXCEEDED, retryAfter);
        }
    }

    public void recordFailedLogin(String identifier, String ipAddress) {
        String key = buildKey(identifier, ipAddress);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, LOGIN_LOCKOUT);
        }
    }

    public void reset(String identifier, String ipAddress) {
        redisTemplate.delete(buildKey(identifier, ipAddress));
    }

    private String buildKey(String identifier, String ipAddress) {
        String normalized = identifier == null ? "" : identifier.toLowerCase();
        String ip = ipAddress == null ? "unknown" : ipAddress;
        return AppConstants.REDIS_LOGIN_ATTEMPTS_PREFIX + normalized + ":" + ip;
    }

    private long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
