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

    private final ResourceService resourceService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Viewer viewer = viewerResolver.resolve(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                resourceService.getFavoriteResources(page, size, viewer),
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
