package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.AuditLogFilterRequest;
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

    public Page<AuditLog> getLogs(AuditLogFilterRequest request, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Action Filter (Multi-select)
            if (request.getAction() != null && !request.getAction().isEmpty()) {
                predicates.add(root.get("action").in(request.getAction()));
            }

            // Target Filter (Multi-select)
            if (request.getTarget() != null && !request.getTarget().isEmpty()) {
                predicates.add(root.get("target").in(request.getTarget()));
            }

            // Username Filter (Partial match)
            if (request.getUsername() != null && !request.getUsername().isEmpty()) {
                predicates
                        .add(cb.like(cb.lower(root.get("username")), "%" + request.getUsername().toLowerCase() + "%"));
            }

            // Date Range Filter
            if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
                try {
                    LocalDate start = LocalDate.parse(request.getStartDate());
                    predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), start.atStartOfDay()));
                } catch (DateTimeParseException e) {
                    log.warn("Invalid startDate format: {}", request.getStartDate());
                }
            }

            if (request.getEndDate() != null && !request.getEndDate().isEmpty()) {
                try {
                    LocalDate end = LocalDate.parse(request.getEndDate());
                    predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), end.atTime(23, 59, 59)));
                } catch (DateTimeParseException e) {
                    log.warn("Invalid endDate format: {}", request.getEndDate());
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }
}
