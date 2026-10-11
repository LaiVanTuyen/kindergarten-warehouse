package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.AuthorProfileResponse;
import com.kindergarten.warehouse.dto.response.DownloadHistoryResponse;
import com.kindergarten.warehouse.dto.response.TagCountResponse;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.PortalDiscoveryService;
import com.kindergarten.warehouse.security.ViewerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PortalDiscoveryController {

    private final PortalDiscoveryService discoveryService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    /** Public author page; list their resources with {@code GET /resources?createdBy={id}}. */
    @GetMapping("/authors/{id}")
    public ResponseEntity<ApiResponse<AuthorProfileResponse>> getAuthor(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(discoveryService.getAuthor(id),
                messageService.getMessage("user.public.success")));
    }

    @GetMapping("/tags/popular")
    public ResponseEntity<ApiResponse<List<TagCountResponse>>> getPopularTags(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(discoveryService.getPopularTags(limit),
                messageService.getMessage("tag.list.success")));
    }

    @GetMapping("/downloads/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DownloadHistoryResponse>>> getMyDownloads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
        return ResponseEntity.ok(ApiResponse.success(
                discoveryService.getDownloadHistory(viewerResolver.resolve(authentication), pageable),
                messageService.getMessage("download.history.success")));
    }
}
