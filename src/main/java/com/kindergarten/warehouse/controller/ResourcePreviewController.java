package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.PreviewResponse;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.ResourcePreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/resources/{id}/preview")
@RequiredArgsConstructor
public class ResourcePreviewController {
    private final ResourcePreviewService previews;
    private final ViewerResolver viewers;

    @GetMapping
    public ResponseEntity<ApiResponse<PreviewResponse>> metadata(
            @PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(previews.describe(id, viewers.resolve(authentication))));
    }

    @GetMapping("/pages/{page}")
    public ResponseEntity<org.springframework.core.io.Resource> page(
            @PathVariable String id, @PathVariable int page, Authentication authentication) {
        // Check authorization again for EACH page; never redirect to a public bucket.
        var image = previews.page(id, page, viewers.resolve(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, "Cookie, Authorization")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_JPEG).body(image);
    }
}
