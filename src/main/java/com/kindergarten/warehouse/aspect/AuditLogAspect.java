package com.kindergarten.warehouse.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kindergarten.warehouse.entity.AuditAction;
import com.kindergarten.warehouse.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    
    // Use a separate ObjectMapper to avoid messing with the global one
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    @AfterReturning(pointcut = "@annotation(com.kindergarten.warehouse.aspect.LogAction)")
    public void logActionAfter(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogAction logAction = method.getAnnotation(LogAction.class);

            AuditAction action = logAction.action();
            String description = logAction.description();

            String username = getCurrentUsername();
            
            // Prioritize target from annotation, fallback to method name
            String target = StringUtils.hasText(logAction.target()) ? logAction.target() : method.getName();

            // Convert args to JSON for better readability
            String argsJson = "[]";
            try {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    // Serialize and then mask sensitive data
                    String rawJson = objectMapper.writeValueAsString(args);
                    argsJson = maskSensitiveData(rawJson);
                }
            } catch (Exception e) {
                // Fallback for circular references or serialization errors
                log.warn("Could not serialize arguments for audit log: {}", e.getMessage());
                argsJson = "[Serialization Error - Check Logs]";
            }

            String detail = description + " | Args: " + argsJson;

            String ipAddress = "UNKNOWN";
            String userAgent = "UNKNOWN";
            try {
                ipAddress = com.kindergarten.warehouse.util.RequestUtils.getClientIpAddress();
                userAgent = com.kindergarten.warehouse.util.RequestUtils.getUserAgent();
            } catch (Exception e) {
                log.warn("Could not retrieve request details: {}", e.getMessage());
            }

            auditLogService.saveLog(action.name(), username, target, detail, ipAddress, userAgent);

        } catch (Exception e) {
            // Ensure logging failure doesn't break business logic
            log.error("Failed to save audit log", e);
        }
    }

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails) {
                    return ((UserDetails) principal).getUsername();
                }
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Could not retrieve current user: {}", e.getMessage());
        }
        return "ANONYMOUS";
    }

    /**
     * Simple regex-based masking for sensitive fields in JSON.
     * Masks values for keys like "password", "token", "confirmPassword", etc.
     */
    private String maskSensitiveData(String json) {
        if (json == null) return null;
        // Regex to find "key": "value" where key is sensitive
        // This is a basic implementation. For complex objects, consider using Jackson Mixins or @JsonIgnore
        return json.replaceAll("(?i)\"(password|passwd|pwd|token|confirmPassword|secret)\"\\s*:\\s*\"[^\"]+\"", "\"$1\": \"******\"");
    }
}
