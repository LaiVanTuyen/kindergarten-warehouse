package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.ReportCreateRequest;
import com.kindergarten.warehouse.dto.request.ReportResolveRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.ReportResponse;
import com.kindergarten.warehouse.entity.ReportStatus;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ResourceReportController {

    private final ResourceReportService reportService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @PostMapping("/api/v1/reports")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReportResponse>> create(
            @Valid @RequestBody ReportCreateRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                reportService.create(request, viewerResolver.resolve(authentication)),
                messageService.getMessage("report.create.success")));
    }

    @GetMapping("/api/v1/admin/reports")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> search(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(reportService.search(status, pageable),
                messageService.getMessage("report.list.success")));
    }

    @GetMapping("/api/v1/admin/reports/open-count")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> openCount() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", reportService.countOpen()),
                messageService.getMessage("report.list.success")));
    }

    @PatchMapping("/api/v1/admin/reports/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<ReportResponse>> resolve(
            @PathVariable Long id, @Valid @RequestBody ReportResolveRequest request, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(reportService.resolve(id, request, principal.getName()),
                messageService.getMessage("report.resolve.success")));
    }
}
