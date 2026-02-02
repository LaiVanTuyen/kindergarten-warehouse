package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.AuditLog;
import com.kindergarten.warehouse.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void saveLog(String action, String username, String target, String detail, String ipAddress,
            String userAgent) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .username(username)
                .target(target)
                .detail(detail)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogs(String action, String username, String startDate, String endDate, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null && !action.isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            if (username != null && !username.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }

            if (startDate != null && !startDate.isEmpty()) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("timestamp"), LocalDateTime.parse(startDate + "T00:00:00")));
            }

            if (endDate != null && !endDate.isEmpty()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), LocalDateTime.parse(endDate + "T23:59:59")));
            }

            // Default sort by timestamp desc if not specified,
            // but Pageable usually handles sort. Providing default fallback in Controller
            // or Client is better.

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }
}
