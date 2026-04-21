package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.util.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Giới hạn số lần login sai theo cặp (identifier + IP) trong một cửa sổ thời gian.
 *
 * <p>Counter đếm được bảo vệ bằng Lua script để đảm bảo {@code INCR + EXPIRE} là 1
 * operation atomic — tránh race giữa các node/thread dẫn đến key "orphan" không có TTL.
 */
@Service
@Slf4j
public class RateLimitService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT = Duration.ofMinutes(15);

    /**
     * INCR counter, nếu là lần đầu (value == 1) thì set TTL.
     * Trả về giá trị counter sau khi tăng.
     */
    private static final DefaultRedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            """
            local v = redis.call('INCR', KEYS[1])
            if v == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return v
            """,
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void ensureLoginAllowed(String identifier, String ipAddress) {
        String key = buildKey(identifier, ipAddress);
        String v = stringRedisTemplate.opsForValue().get(key);
        long attempts = parseLong(v);
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = ttl == null || ttl <= 0 ? LOGIN_LOCKOUT.toSeconds() : ttl;
            throw AppException.withRetryAfter(ErrorCode.LOGIN_ATTEMPT_EXCEEDED, retryAfter);
        }
    }

    public void recordFailedLogin(String identifier, String ipAddress) {
        String key = buildKey(identifier, ipAddress);
        stringRedisTemplate.execute(
                INCR_WITH_TTL,
                List.of(key),
                String.valueOf(LOGIN_LOCKOUT.toSeconds()));
    }

    public void reset(String identifier, String ipAddress) {
        stringRedisTemplate.delete(buildKey(identifier, ipAddress));
    }

    private String buildKey(String identifier, String ipAddress) {
        String normalized = identifier == null ? "" : identifier.toLowerCase();
        String ip = ipAddress == null ? "unknown" : ipAddress;
        return AppConstants.REDIS_LOGIN_ATTEMPTS_PREFIX + normalized + ":" + ip;
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
