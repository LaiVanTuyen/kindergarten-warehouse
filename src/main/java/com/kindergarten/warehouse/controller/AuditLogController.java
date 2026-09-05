package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.AuditLogFilterRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.AuditLogResponse;
import com.kindergarten.warehouse.service.AuditLogService;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

        private static final Set<String> SORT_FIELDS = Set.of(
                        "id", "action", "username", "target", "ipAddress", "timestamp");

        private final AuditLogService auditLogService;
        private final MessageService messageService;

        @GetMapping
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
                        @ModelAttribute AuditLogFilterRequest filterRequest,
                        @PageableDefault(size = 10, sort = "timestamp", direction = Sort.Direction.DESC) Pageable requestedPageable) {

                Pageable pageable = PageableUtils.sanitize(
                                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "timestamp"));

                Page<AuditLogResponse> logs = auditLogService.getLogs(filterRequest, pageable);

                return ResponseEntity.ok(ApiResponse.success(logs, "Audit logs retrieved successfully"));
        }

        @GetMapping("/export")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportAuditLogs(
                        @ModelAttribute AuditLogFilterRequest filterRequest,
                        @PageableDefault(size = 10, sort = "timestamp", direction = Sort.Direction.DESC) Pageable requestedPageable) {

                Sort sort = PageableUtils.sanitizeSort(
                                requestedPageable.getSort(), SORT_FIELDS, Sort.by(Sort.Direction.DESC, "timestamp"));

                org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream = out -> {
                        auditLogService.exportLogsToStream(filterRequest, sort, out);
                };

                return ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"audit_logs.csv\"")
                                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                                .body(stream);
        }
}
