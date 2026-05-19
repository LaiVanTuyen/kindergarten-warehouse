package com.kindergarten.warehouse.aspect;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class RateLimitingAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitingAspect(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("execution(* com.kindergarten.warehouse.controller.ResourceController.viewResource(..))")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String ipAddress = request.getRemoteAddr();
        String resourceId = request.getRequestURI().split("/")[4]; // Assuming /api/v1/resources/{id}/view

        String key = "kindergarten:rate_limit:" + ipAddress + ":" + resourceId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.debug("View rate limit exceeded for IP={} resource={}", ipAddress, resourceId);
            throw AppException.withRetryAfter(ErrorCode.RESOURCE_VIEW_RATE_LIMIT_EXCEEDED, 60);
        }

        redisTemplate.opsForValue().set(key, "1", 60, TimeUnit.SECONDS); // 1 minute TTL

        return joinPoint.proceed();
    }
}
