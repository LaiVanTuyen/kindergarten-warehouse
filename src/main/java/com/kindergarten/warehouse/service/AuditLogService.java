package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.AuditLog;
import com.kindergarten.warehouse.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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
                try {
                    // Try parsing as LocalDate first (yyyy-MM-dd)
                    LocalDate start = LocalDate.parse(startDate);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), start.atStartOfDay()));
                } catch (DateTimeParseException e) {
                    log.warn("Invalid startDate format: {}", startDate);
                    // Optionally throw exception or ignore filter
                }
            }

            if (endDate != null && !endDate.isEmpty()) {
                try {
                    LocalDate end = LocalDate.parse(endDate);
                    predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), end.atTime(23, 59, 59)));
                } catch (DateTimeParseException e) {
                    log.warn("Invalid endDate format: {}", endDate);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }
}
