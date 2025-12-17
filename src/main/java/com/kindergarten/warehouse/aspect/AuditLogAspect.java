package com.kindergarten.warehouse.aspect;

import com.kindergarten.warehouse.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @AfterReturning(pointcut = "@annotation(com.kindergarten.warehouse.aspect.LogAction)")
    public void logActionAfter(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogAction logAction = method.getAnnotation(LogAction.class);

            String action = logAction.action();
            String description = logAction.description();

            String username = getCurrentUsername();
            String target = method.getName(); // Default target is method name, can be refined
            String detail = description + " Args: " + Arrays.toString(joinPoint.getArgs());

            // Refine target based on arguments if possible
            if (joinPoint.getArgs().length > 0) {
                target += " " + joinPoint.getArgs()[0];
            }

            auditLogService.saveLog(action, username, target, detail);

        } catch (Exception e) {
            // Ensure logging failure doesn't break business logic
            e.printStackTrace();
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SYSTEM"; // Or "ANONYMOUS"
    }
}
