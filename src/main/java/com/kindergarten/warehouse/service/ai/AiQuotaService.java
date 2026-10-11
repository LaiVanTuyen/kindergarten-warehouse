package com.kindergarten.warehouse.service.ai;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Per-teacher daily cap on AI requests (each request may call the model twice).
 * Counted in Redis per Vietnam calendar day. If Redis is down the request is refused:
 * failing open would let spend run unbounded.
 */
@Slf4j
@Service
public class AiQuotaService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StringRedisTemplate redis;
    private final int dailyLimit;

    public AiQuotaService(StringRedisTemplate redis, @Value("${ai.daily-limit-per-user:30}") int dailyLimit) {
        this.redis = redis;
        this.dailyLimit = dailyLimit;
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    /** Takes one request from today's allowance; returns what is left after it. */
    public int consume(Long userId) {
        String key = key(userId);
        Long used;
        try {
            used = redis.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redis.expire(key, Duration.ofHours(25));
            }
        } catch (RuntimeException e) {
            log.error("AI quota check failed (Redis): {}", e.getMessage());
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
        if (used == null || used > dailyLimit) {
            throw new AppException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        return (int) (dailyLimit - used);
    }

    public int remaining(Long userId) {
        try {
            String value = redis.opsForValue().get(key(userId));
            int used = value == null ? 0 : Integer.parseInt(value);
            return Math.max(0, dailyLimit - used);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String key(Long userId) {
        return "ai:quota:" + LocalDate.now(VN) + ":" + userId;
    }
}
