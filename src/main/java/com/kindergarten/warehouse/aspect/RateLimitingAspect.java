package com.kindergarten.warehouse.aspect;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
@Slf4j
public class RateLimitingAspect {

    private static final Duration VIEW_RATE_LIMIT = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitingAspect(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("execution(* com.kindergarten.warehouse.controller.ResourceController.incrementViewCount(..))")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String ipAddress = RequestUtils.getClientIpAddress();
        String resourceId = extractResourceId(request.getRequestURI());

        if (resourceId == null) {
            return joinPoint.proceed();
        }

        String key = "kindergarten:rate_limit:" + ipAddress + ":" + resourceId;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", VIEW_RATE_LIMIT);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("View rate limit exceeded for IP={} resource={}", ipAddress, resourceId);
            throw AppException.withRetryAfter(ErrorCode.RESOURCE_VIEW_RATE_LIMIT_EXCEEDED,
                    VIEW_RATE_LIMIT.toSeconds());
        }

        return joinPoint.proceed();
    }

    private String extractResourceId(String uri) {
        if (uri == null) return null;
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("resources".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }
}
