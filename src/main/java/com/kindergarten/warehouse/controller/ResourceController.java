package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

        private final ResourceService resourceService;
        private final MessageService messageService;

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<ResourceResponse>> uploadResource(
                        @Valid @ModelAttribute ResourceCreationRequest request,
                        Principal principal) {

                return new ResponseEntity<>(
                                ApiResponse.success(
                                                resourceService.uploadResource(request, principal.getName()),
                                                messageService.getMessage("resource.upload.success")),
                                HttpStatus.CREATED);
        }

        @GetMapping
        public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getPortalResources(
                        @ModelAttribute ResourceFilterRequest filterRequest,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size) {
                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.getPortalResources(filterRequest, page, size),
                                                messageService.getMessage("resource.list.success")),
                                HttpStatus.OK);
        }

        @GetMapping("/me")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getMyResources(
                        @ModelAttribute ResourceFilterRequest filterRequest,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size,
                        Principal principal) {
                return new ResponseEntity<>(
                                ApiResponse.success(
                                                resourceService.getMyResources(filterRequest, page, size,
                                                                principal.getName()),
                                                messageService.getMessage("resource.list.success")),
                                HttpStatus.OK);
        }

        @GetMapping("/{slug}")
        public ResponseEntity<ApiResponse<ResourceResponse>> getResourceBySlug(@PathVariable String slug) {
                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.getResourceBySlug(slug),
                                                messageService.getMessage("resource.detail.success")),
                                HttpStatus.OK);
        }

        @PutMapping("/{id}/view")
        public ResponseEntity<ApiResponse<Void>> incrementViewCount(
                        @PathVariable String id,
                        HttpServletRequest request) {
                String ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty()) {
                        ipAddress = request.getRemoteAddr();
                }
                if (ipAddress != null && ipAddress.contains(",")) {
                        ipAddress = ipAddress.split(",")[0].trim();
                }

                resourceService.incrementViewCount(id, ipAddress);
                return ResponseEntity
                                .ok(ApiResponse.success(null,
                                                messageService.getMessage("resource.view.increment.success")));
        }

        // ✅ CRITICAL FIX #1: Add file download endpoint
        @GetMapping("/{id}/file")
        public ResponseEntity<?> downloadResource(@PathVariable String id) {
                try {
                        // Get file stream
                        var fileInfo = resourceService.getResourceFileInfo(id);
                        String cleanFileName = fileInfo.getFileName();
                        String encodedFileName = java.net.URLEncoder
                                        .encode(cleanFileName, java.nio.charset.StandardCharsets.UTF_8)
                                        .replace("+", "%20");

                        return ResponseEntity.ok()
                                        .header("Content-Disposition",
                                                        "attachment; filename=\"" + cleanFileName
                                                                        + "\"; filename*=UTF-8''" + encodedFileName)
                                        .header("Content-Type", fileInfo.getContentType())
                                        .body(fileInfo.getInputStream());

                } catch (IllegalArgumentException e) {
                        log.warn("Cannot download resource {}: {}", id, e.getMessage());
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), e.getMessage()));
                } catch (Exception e) {
                        log.error("Error downloading resource {}: {}", id, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode(),
                                                        "Failed to download file"));
                }
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable String id, Principal principal,
                        @RequestParam(defaultValue = "false") boolean hard) {
                resourceService.deleteResource(id, principal.getName(), hard);
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("resource.delete.success")));
        }

        @DeleteMapping("/bulk")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> deleteResources(
                        @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 1000, message = "{validation.size}") java.util.List<String> ids,
                        Principal principal,
                        @RequestParam(defaultValue = "false") boolean hard) {
                resourceService.deleteResources(ids, principal.getName(), hard);
                return ResponseEntity.ok(ApiResponse.success(null,
                                messageService.getMessage("resource.delete.bulk.success")));
        }

        @PutMapping("/{id}/restore")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> restoreResource(@PathVariable String id, Principal principal) {
                resourceService.restoreResource(id, principal.getName());
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("resource.restore.success")));
        }

        @PatchMapping("/bulk-restore")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> restoreResources(
                        @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 1000, message = "{validation.size}") java.util.List<String> ids,
                        Principal principal) {
                resourceService.restoreResources(ids, principal.getName());
                return ResponseEntity.ok(ApiResponse.success(null,
                                messageService.getMessage("resource.restore.bulk.success")));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<ResourceResponse>> updateResourceJson(
                        @PathVariable String id,
                        @Valid @RequestBody ResourceUpdateRequest request,
                        Principal principal) {

                return ResponseEntity.ok(ApiResponse.success(
                                resourceService.updateResource(id, request, principal.getName()),
                                messageService.getMessage("resource.update.success")));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<ResourceResponse>> updateResourceForm(
                        @PathVariable String id,
                        @Valid @ModelAttribute ResourceUpdateRequest request,
                        Principal principal) {

                return ResponseEntity.ok(ApiResponse.success(
                                resourceService.updateResource(id, request, principal.getName()),
                                messageService.getMessage("resource.update.success")));
        }

        @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Map<String, String>>> updateThumbnail(
                        @PathVariable String id,
                        @RequestParam("thumbnail") MultipartFile thumbnail,
                        Principal principal) {

                String thumbnailUrl = resourceService.updateThumbnail(id, thumbnail, principal.getName());
                return ResponseEntity.ok(ApiResponse.success(
                                Collections.singletonMap("thumbnailUrl", thumbnailUrl),
                                messageService.getMessage("resource.thumbnail.success")));
        }

        @PostMapping("/{id}/favorite")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleFavorite(@PathVariable String id,
                        Principal principal) {
                boolean isFavorited = resourceService.toggleFavorite(id, principal.getName());
                return ResponseEntity
                                .ok(ApiResponse.success(Collections.singletonMap("isFavorited", isFavorited),
                                                messageService.getMessage("resource.favorite.success")));
        }

        @PatchMapping(value = "/{id}/visibility", consumes = MediaType.APPLICATION_JSON_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<ResourceResponse>> updateVisibility(
                        @PathVariable String id,
                        @Valid @RequestBody com.kindergarten.warehouse.dto.request.VisibilityUpdateRequest request,
                        Principal principal) {
                return ResponseEntity.ok(ApiResponse.success(
                                resourceService.updateVisibility(id, request, principal.getName()),
                                messageService.getMessage("resource.update.success")));
        }
}
