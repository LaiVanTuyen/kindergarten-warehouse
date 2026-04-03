package com.kindergarten.warehouse.scheduler;

import com.kindergarten.warehouse.repository.ResourceRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
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

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void syncViewsToDatabase() {
        Set<Object> dirtyResourceIds = redisTemplate.opsForSet().members(DIRTY_VIEWS_SET);
        if (dirtyResourceIds == null || dirtyResourceIds.isEmpty()) {
            return;
        }

        for (Object idObj : dirtyResourceIds) {
            String id = (String) idObj;
            String key = VIEWS_KEY_PREFIX + id;
            Object countObj = redisTemplate.opsForValue().getAndSet(key, 0); // Atomic get and clear

            if (countObj != null) {
                long count = Long.parseLong(countObj.toString());
                if (count > 0) {
                    resourceRepository.incrementViews(id, count);
                }
            }
            redisTemplate.opsForSet().remove(DIRTY_VIEWS_SET, id);
        }
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void syncDownloadsToDatabase() {
        Set<Object> dirtyResourceIds = redisTemplate.opsForSet().members(DIRTY_DOWNLOADS_SET);
        if (dirtyResourceIds == null || dirtyResourceIds.isEmpty()) {
            return;
        }

        for (Object idObj : dirtyResourceIds) {
            String id = (String) idObj;
            String key = DOWNLOADS_KEY_PREFIX + id;
            Object countObj = redisTemplate.opsForValue().getAndSet(key, 0);

            if (countObj != null) {
                long count = Long.parseLong(countObj.toString());
                if (count > 0) {
                    resourceRepository.incrementDownloads(id, count);
                }
            }
            redisTemplate.opsForSet().remove(DIRTY_DOWNLOADS_SET, id);
        }
    }
}
