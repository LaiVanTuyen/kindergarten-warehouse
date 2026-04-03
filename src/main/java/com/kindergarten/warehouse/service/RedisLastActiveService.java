package com.kindergarten.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kindergarten.warehouse.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLastActiveService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String KEY_PREFIX = "user:last_active:";
    private static final long THROTTLE_MINUTES = 15;
    private static final int SCAN_BATCH_SIZE = 100;

    public void updateLastActive(Long userId) {
        String key = KEY_PREFIX + userId;

        // Throttling: Check if key exists (meaning updated recently)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }

        // Save timestamp (Epoch Second) to Redis with TTL = throttle time
        redisTemplate.opsForValue().set(
                key,
                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                THROTTLE_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * Scheduled Job: Sync from Redis to DB every 10 minutes.
     *
     * <p>Uses SCAN instead of KEYS to avoid blocking Redis on production.
     * Batch-updates the DB to minimize the number of round-trips.
     */
    @Scheduled(fixedRate = 600_000) // 10 minutes
    @Transactional
    public void syncLastActiveToDatabase() {
        log.info("Starting sync Last Active from Redis to DB...");

        List<Long> userIds = new ArrayList<>();

        // SCAN thay vì KEYS để không block Redis
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*")
                .count(SCAN_BATCH_SIZE)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    String userIdStr = key.replace(KEY_PREFIX, "");
                    userIds.add(Long.parseLong(userIdStr));
                } catch (NumberFormatException e) {
                    log.warn("Invalid last_active key format: {}", key);
                }
            }
        } catch (Exception e) {
            log.error("Error scanning Redis for last_active keys", e);
            return;
        }

        if (userIds.isEmpty()) {
            return;
        }

        // Batch update: 1 câu UPDATE duy nhất thay vì N câu
        LocalDateTime now = LocalDateTime.now();
        try {
            userRepository.batchUpdateLastActive(userIds, now);
            log.info("Synced {} users last active time.", userIds.size());
        } catch (Exception e) {
            log.error("Failed to batch update last_active for {} users", userIds.size(), e);
        }
    }
}
