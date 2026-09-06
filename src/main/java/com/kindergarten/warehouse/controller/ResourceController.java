package com.kindergarten.warehouse.controller;

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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.kindergarten.warehouse.util.PageableUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    /** API_CONTRACT_V2 §0.5: whitelist sort RIENG cua endpoint nay. */
    private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of(
            "id", "title", "createdAt", "updatedAt", "viewsCount", "downloadCount", "averageRating");

    private static final org.springframework.data.domain.Sort DEFAULT_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt");

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
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable requestedPageable,
            org.springframework.security.core.Authentication authentication) {
        Pageable pageable = PageableUtils.sanitize(requestedPageable, SORT_FIELDS, DEFAULT_SORT);
        return new ResponseEntity<>(
                ApiResponse.success(resourceService.getPortalResources(
                                filterRequest, pageable, viewerResolver.resolve(authentication)),
                        messageService.getMessage("resource.list.success")),
                HttpStatus.OK);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getMyResources(
            @ModelAttribute ResourceFilterRequest filterRequest,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable requestedPageable,
            Principal principal) {
        Pageable pageable = PageableUtils.sanitize(requestedPageable, SORT_FIELDS, DEFAULT_SORT);
        return new ResponseEntity<>(
                ApiResponse.success(
                        resourceService.getMyResources(filterRequest, pageable, principal.getName()),
                        messageService.getMessage("resource.list.success")),
                HttpStatus.OK);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<ResourceResponse>> getResourceBySlug(
            @PathVariable String slug,
            org.springframework.security.core.Authentication authentication) {
        var viewer = viewerResolver.resolve(authentication);
        return new ResponseEntity<>(
                ApiResponse.success(resourceService.getResourceBySlug(slug, viewer),
                        messageService.getMessage("resource.detail.success")),
                HttpStatus.OK);
    }

    /**
     * API_CONTRACT_V2 §—dong 234: <strong>POST</strong> {@code /{id}/view}.
     *
     * <p>Truoc day khai {@code @PutMapping} trong khi FE da gui POST
     * ({@code resource.service.ts:264}) — moi luot xem tra 405 va khong duoc
     * dem. Doi ve POST cho khop hop dong va cho FE.
     */
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

    /**
     * API_CONTRACT_V2 §—dong 235: {@code GET /{id}/file}, che do {@code stream}
     * (BUSINESS_RULES §8.3).
     *
     * <p>Return type phai la {@code ResponseEntity<StreamingResponseBody>} chu
     * KHONG phai {@code <?>}: neu khong Spring khong route vao
     * {@code StreamingResponseBodyReturnValueHandler} ma co serialize lambda
     * bang message converter -> {@code HttpMessageNotWritableException}.
     *
     * <p>Khong con khoi {@code try/catch} tra ResponseEntity loi: moi loi
     * (khong tim thay / khong co quyen / youtube / storage) deu la AppException
     * va {@code GlobalExceptionHandler} tra JSON {@code ApiResponse} dung
     * contract. Bat rong roi tu dung body khac kieu la cach lam mat 401/410 ma
     * ResourceAccessGuard vua tinh ra.
     *
     * <p>Bo dem luot tai o day — {@code getResourceFileInfo} da goi
     * {@code resourceStatService.incrementDownloadCount} roi.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<StreamingResponseBody> downloadResource(
            @PathVariable String id,
            org.springframework.security.core.Authentication authentication) throws Exception {
        var fileInfo = resourceService.getResourceFileInfo(id, viewerResolver.resolve(authentication));

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(fileInfo.getFileName(), StandardCharsets.UTF_8)
                .build();

        StreamingResponseBody stream = outputStream -> {
            try (java.io.InputStream inputStream = fileInfo.getInputStream()) {
                inputStream.transferTo(outputStream);
            }
        };

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()));

        // Chỉ khai Content-Length khi biết chắc. Khai 0 cho một tệp có nội dung
        // còn tệ hơn không khai: client tin header và dừng đọc ngay.
        if (fileInfo.getFileSize() > 0) {
            builder.contentLength(fileInfo.getFileSize());
        }

        return builder.body(stream);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteResource(
            @PathVariable String id,
            Principal principal,
            @RequestParam(defaultValue = "false") boolean hard) {
        resourceService.deleteResource(id, principal.getName(), hard);
        return ResponseEntity
                .ok(ApiResponse.success(null, messageService.getMessage("resource.delete.success")));
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteResources(
            @RequestBody @jakarta.validation.constraints.Size(
                    min = 1, max = 1000, message = "{validation.size}") java.util.List<String> ids,
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
            @RequestBody @jakarta.validation.constraints.Size(
                    min = 1, max = 1000, message = "{validation.size}") java.util.List<String> ids,
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
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleFavorite(
            @PathVariable String id,
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
