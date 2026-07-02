package com.kindergarten.warehouse.scheduler;

import com.kindergarten.warehouse.repository.ResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ResourceRepository resourceRepository;

    private static final String VIEWS_KEY_PREFIX = "kindergarten:views:";
    private static final String DOWNLOADS_KEY_PREFIX = "kindergarten:downloads:";
    private static final String DIRTY_VIEWS_SET = "kindergarten:dirty_views";
    private static final String DIRTY_DOWNLOADS_SET = "kindergarten:dirty_downloads";

    public RedisSyncScheduler(RedisTemplate<String, Object> redisTemplate, ResourceRepository resourceRepository) {
        this.redisTemplate = redisTemplate;
        this.resourceRepository = resourceRepository;
    }

    @Scheduled(fixedRate = 300_000)
    public void syncViewsToDatabase() {
        drainDirtySet(DIRTY_VIEWS_SET, VIEWS_KEY_PREFIX,
                (id, count) -> resourceRepository.incrementViews(id, count));
    }

    @Scheduled(fixedRate = 300_000)
    public void syncDownloadsToDatabase() {
        drainDirtySet(DIRTY_DOWNLOADS_SET, DOWNLOADS_KEY_PREFIX,
                (id, count) -> resourceRepository.incrementDownloads(id, count));
    }

    /**
     * Atomic drain pattern: SPOP từng id khỏi dirty set, GETSET counter về 0,
     * UPDATE DB. Nếu UPDATE fail → restore counter và re-add id vào dirty set
     * để vòng sync sau xử lý lại — không mất count.
     */
    private void drainDirtySet(String dirtySet, String counterPrefix, java.util.function.BiConsumer<String, Long> dbWriter) {
        Object popped;
        while ((popped = redisTemplate.opsForSet().pop(dirtySet)) != null) {
            String id = popped.toString();
            String counterKey = counterPrefix + id;

            Object countObj = redisTemplate.opsForValue().getAndSet(counterKey, 0);
            long count = parseLongSafe(countObj);
            if (count <= 0) {
                continue;
            }

            try {
                dbWriter.accept(id, count);
            } catch (Exception e) {
                log.error("Sync failed for {}={}, restoring count={} to Redis", counterPrefix, id, count, e);
                try {
                    redisTemplate.opsForValue().increment(counterKey, count);
                    redisTemplate.opsForSet().add(dirtySet, id);
                } catch (Exception restoreEx) {
                    log.error("CRITICAL: failed to restore Redis state for {}{}, count={} lost",
                            counterPrefix, id, count, restoreEx);
                }
            }
        }
    }

    private long parseLongSafe(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
