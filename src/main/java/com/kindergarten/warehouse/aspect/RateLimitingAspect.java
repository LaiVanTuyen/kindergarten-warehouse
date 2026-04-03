package com.kindergarten.warehouse.aspect;

import jakarta.servlet.http.HttpServletRequest;
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
            throw new RuntimeException("Rate limit exceeded. Try again later.");
        }

        redisTemplate.opsForValue().set(key, "1", 60, TimeUnit.SECONDS); // 1 minute TTL

        return joinPoint.proceed();
    }
}
