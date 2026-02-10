package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.service.ResourceStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ResourceStatServiceImpl implements ResourceStatService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ResourceRepository resourceRepository;

    @Override
    public void incrementViewCount(String resourceId, String ipAddress) {
        String key = "view_tracking:" + ipAddress + ":" + resourceId;
        
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }

        if (!resourceRepository.existsById(resourceId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        redisTemplate.opsForValue().increment("kindergarten:views:" + resourceId);
        redisTemplate.opsForSet().add("kindergarten:dirty_views", resourceId);

        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), 1, TimeUnit.HOURS);
    }

    @Override
    public void incrementDownloadCount(String resourceId) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        redisTemplate.opsForValue().increment("kindergarten:downloads:" + resourceId);
        redisTemplate.opsForSet().add("kindergarten:dirty_downloads", resourceId);
    }
}
