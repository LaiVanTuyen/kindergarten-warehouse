package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.service.ResourceStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ResourceStatServiceImpl implements ResourceStatService {

    private static final String VIEWS_KEY_PREFIX = "kindergarten:views:";
    private static final String DOWNLOADS_KEY_PREFIX = "kindergarten:downloads:";
    private static final String DIRTY_VIEWS_SET = "kindergarten:dirty_views";
    private static final String DIRTY_DOWNLOADS_SET = "kindergarten:dirty_downloads";
    private static final String VIEW_TRACKING_PREFIX = "view_tracking:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ResourceRepository resourceRepository;

    @Override
    public void incrementViewCount(String resourceId, String ipAddress) {
        String trackingKey = VIEW_TRACKING_PREFIX + ipAddress + ":" + resourceId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(trackingKey))) {
            return;
        }

        if (!resourceRepository.existsById(resourceId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        redisTemplate.opsForValue().increment(VIEWS_KEY_PREFIX + resourceId);
        redisTemplate.opsForSet().add(DIRTY_VIEWS_SET, resourceId);

        redisTemplate.opsForValue().set(trackingKey, String.valueOf(System.currentTimeMillis()), 1, TimeUnit.HOURS);
    }

    @Override
    public void incrementDownloadCount(String resourceId) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        redisTemplate.opsForValue().increment(DOWNLOADS_KEY_PREFIX + resourceId);
        redisTemplate.opsForSet().add(DIRTY_DOWNLOADS_SET, resourceId);
    }

    @Override
    public long getPendingViewCount(String resourceId) {
        return readCounter(VIEWS_KEY_PREFIX + resourceId);
    }

    @Override
    public long getPendingDownloadCount(String resourceId) {
        return readCounter(DOWNLOADS_KEY_PREFIX + resourceId);
    }

    @Override
    public Map<String, Long> getPendingViewCounts(Collection<String> resourceIds) {
        return bulkRead(resourceIds, VIEWS_KEY_PREFIX);
    }

    @Override
    public Map<String, Long> getPendingDownloadCounts(Collection<String> resourceIds) {
        return bulkRead(resourceIds, DOWNLOADS_KEY_PREFIX);
    }

    private Map<String, Long> bulkRead(Collection<String> resourceIds, String prefix) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> orderedIds = new ArrayList<>(resourceIds);
        List<String> keys = new ArrayList<>(orderedIds.size());
        for (String id : orderedIds) {
            keys.add(prefix + id);
        }
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        Map<String, Long> result = new HashMap<>(orderedIds.size() * 2);
        for (int i = 0; i < orderedIds.size(); i++) {
            Object raw = (values != null && i < values.size()) ? values.get(i) : null;
            result.put(orderedIds.get(i), parseLongSafe(raw));
        }
        return result;
    }

    private long readCounter(String key) {
        Object raw = redisTemplate.opsForValue().get(key);
        return parseLongSafe(raw);
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
