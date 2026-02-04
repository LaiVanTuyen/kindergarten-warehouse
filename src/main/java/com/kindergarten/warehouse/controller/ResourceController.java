package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

        private final ResourceService resourceService;
        private final MessageService messageService;
        private final UserRepository userRepository;

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
        public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getResources(
                        @ModelAttribute ResourceFilterRequest filterRequest,
                        @RequestParam(value = "mine", required = false, defaultValue = "false") boolean mine,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size,
                        Principal principal) {

                if (mine && principal != null) {
                    User user = userRepository.findByUsername(principal.getName()).orElse(null);
                    if (user != null) {
                        filterRequest.setCreatedBy(user.getId());
                    }
                }

                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.getResources(filterRequest, page, size),
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

        @PutMapping("/{id}/download")
        public ResponseEntity<ApiResponse<Void>> incrementDownloadCount(@PathVariable String id) {
                resourceService.incrementDownloadCount(id);
                return ResponseEntity.ok(ApiResponse.success(null, "Download count incremented"));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable String id) {
                resourceService.deleteResource(id);
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("resource.delete.success")));
        }

        @PutMapping("/{id}/restore")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Void>> restoreResource(@PathVariable String id) {
                resourceService.restoreResource(id);
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("resource.restore.success")));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<ResourceResponse>> updateResource(
                @PathVariable String id,
                @RequestBody ResourceUpdateRequest request) {
            
            return ResponseEntity.ok(ApiResponse.success(
                    resourceService.updateResource(id, request),
                    messageService.getMessage("resource.update.success")));
        }

        @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
        public ResponseEntity<ApiResponse<Map<String, String>>> updateThumbnail(
                @PathVariable String id,
                @RequestParam("thumbnail") MultipartFile thumbnail) {
            
            String thumbnailUrl = resourceService.updateThumbnail(id, thumbnail);
            return ResponseEntity.ok(ApiResponse.success(
                    Collections.singletonMap("thumbnailUrl", thumbnailUrl),
                    "Thumbnail updated successfully"));
        }

        @PostMapping("/{id}/favorite")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<Void>> toggleFavorite(@PathVariable String id, Principal principal) {
            resourceService.toggleFavorite(id, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(null, "Favorite status toggled"));
        }
}
