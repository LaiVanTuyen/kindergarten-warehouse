package com.kindergarten.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kindergarten.warehouse.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLastActiveService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String KEY_PREFIX = "user:last_active:";
    private static final long THOTTLE_MINUTES = 15;

    public void updateLastActive(Long userId) {
        String key = KEY_PREFIX + userId;

        // Throttling: Check if key exists (meaning updated recently)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }

        // Save timestamp (Epoch Second) to Redis
        // Set TTL to 15 mins (longer than throttle time)
        redisTemplate.opsForValue().set(key, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC), THOTTLE_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * Scheduled Job: Sync from Redis to DB
     * Run every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    @Transactional
    public void syncLastActiveToDatabase() {
        log.info("Starting sync Last Active from Redis to DB...");

        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int count = 0;
        for (String key : keys) {
            try {
                String userIdStr = key.replace(KEY_PREFIX, "");
                Long userId = Long.parseLong(userIdStr);

                // We actually don't need the exact timestamp from Redis because "NOW" is close
                // enough for "Last Active"
                // But if we want precision, we can get it.
                // Object timestampObj = redisTemplate.opsForValue().get(key);

                // Update DB: Set last_active = NOW()
                userRepository.updateLastActive(userId, LocalDateTime.now());

                // We DON'T delete the key here, we let it expire naturally (TTL).
                // Why? If we delete, the next request will trigger a Redis write again
                // immediately.
                // By keeping it, we maintain the "Throttling" for 15 minutes.
                count++;
            } catch (Exception e) {
                log.error("Failed to sync last active for key: {}", key, e);
            }
        }

        log.info("Synced {} users last active time.", count);
    }
}
