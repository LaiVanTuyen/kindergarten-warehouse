package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.BulkResourceRequest;
import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.kindergarten.warehouse.util.PageableUtils;

@Slf4j
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

        private static final Set<String> SORT_FIELDS = Set.of(
                        "id", "title", "slug", "status", "visibility", "resourceType", "fileType",
                        "viewsCount", "downloadCount", "averageRating", "createdBy", "createdAt", "updatedAt");

        private final ResourceService resourceService;
        private final MessageService messageService;
        private final com.kindergarten.warehouse.security.ViewerResolver viewerResolver;

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<ResourceResponse>> uploadResource(
                        @Valid @ModelAttribute ResourceCreationRequest request,
                        Principal principal) {

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.success(
                                                resourceService.uploadResource(request, principal.getName()),
                                                messageService.getMessage("resource.upload.success")));
        }

        @GetMapping
        public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getPortalResources(
                        @ModelAttribute ResourceFilterRequest filterRequest,
                        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable requestedPageable) {
                Pageable pageable = PageableUtils.sanitize(
                                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.getPortalResources(filterRequest, pageable),
                                                messageService.getMessage("resource.list.success")),
                                HttpStatus.OK);
        }

        @GetMapping("/me")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getMyResources(
                        @ModelAttribute ResourceFilterRequest filterRequest,
                        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable requestedPageable,
                        Principal principal) {
                Pageable pageable = PageableUtils.sanitize(
                                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
                return new ResponseEntity<>(
                                ApiResponse.success(
                                                resourceService.getMyResources(filterRequest, pageable,
                                                                principal.getName()),
                                                messageService.getMessage("resource.list.success")),
                                HttpStatus.OK);
        }

        @GetMapping("/{slug}")
        public ResponseEntity<ApiResponse<ResourceResponse>> getResourceBySlug(
                        @PathVariable String slug,
                        org.springframework.security.core.Authentication authentication) {
                // Lấy Viewer ở biên rồi truyền tường minh xuống service, thay vì để
                // service tự đọc SecurityContextHolder.
                var viewer = viewerResolver.resolve(authentication);
                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.getResourceBySlug(slug, viewer),
                                                messageService.getMessage("resource.detail.success")),
                                HttpStatus.OK);
        }

        @PostMapping("/{id}/view")
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

        @GetMapping("/{id}/file")
        public ResponseEntity<StreamingResponseBody> downloadResource(@PathVariable String id) throws Exception {
                // Return type phải là ResponseEntity<StreamingResponseBody> (không phải <?>),
                // nếu không Spring không route vào StreamingResponseBodyReturnValueHandler ->
                // cố serialize lambda bằng message converter -> HttpMessageNotWritableException.
                // Lỗi (không tìm thấy / forbidden / youtube / storage) ném AppException ->
                // GlobalExceptionHandler trả JSON ApiResponse (theo contract).
                var fileInfo = resourceService.getResourceFileInfo(id);
                ContentDisposition contentDisposition = ContentDisposition.attachment()
                                .filename(fileInfo.getFileName(), StandardCharsets.UTF_8)
                                .build();

                StreamingResponseBody stream = outputStream -> {
                        try (InputStream inputStream = fileInfo.getInputStream()) {
                                inputStream.transferTo(outputStream);
                        }
                };

                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                                .contentLength(fileInfo.getFileSize())
                                .body(stream);
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable String id, Principal principal,
                        @RequestParam(defaultValue = "false") boolean hard) {
                resourceService.deleteResource(id, principal.getName(), hard);
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("resource.delete.success")));
        }

        @PostMapping("/bulk-delete")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> deleteResources(
                        @Valid @RequestBody BulkResourceRequest request,
                        Principal principal,
                        @RequestParam(defaultValue = "false") boolean hard) {
                resourceService.deleteResources(request.getIds(), principal.getName(), hard);
                return ResponseEntity.ok(ApiResponse.success(null,
                                messageService.getMessage("resource.delete.bulk.success")));
        }

        @PatchMapping("/{id}/restore")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> restoreResource(@PathVariable String id, Principal principal) {
                resourceService.restoreResource(id, principal.getName());
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("resource.restore.success")));
        }

        @PatchMapping("/bulk-restore")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> restoreResources(
                        @Valid @RequestBody BulkResourceRequest request,
                        Principal principal) {
                resourceService.restoreResources(request.getIds(), principal.getName());
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
