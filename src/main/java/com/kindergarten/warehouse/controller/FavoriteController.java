package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FavoriteController {

    /** API_CONTRACT_V2 §0.5: whitelist sort RIENG cua endpoint nay. */
    private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of(
            "id", "title", "createdAt", "updatedAt", "viewsCount", "downloadCount", "averageRating");

    private static final org.springframework.data.domain.Sort DEFAULT_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt");

    private final ResourceService resourceService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getFavorites(
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable requestedPageable,
            Authentication authentication) {
        Viewer viewer = viewerResolver.resolve(authentication);
        org.springframework.data.domain.Pageable pageable =
                com.kindergarten.warehouse.util.PageableUtils.sanitize(requestedPageable, SORT_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(ApiResponse.success(
                resourceService.getFavoriteResources(pageable, viewer),
                messageService.getMessage("resource.list.success")));
    }

    @GetMapping("/ids")
    public ResponseEntity<ApiResponse<List<String>>> getFavoriteIds(Authentication authentication) {
        Viewer viewer = viewerResolver.resolve(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                resourceService.getFavoriteResourceIds(viewer),
                messageService.getMessage("resource.list.success")));
    }
}
