package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.BannerRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.BannerResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.service.BannerService;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.util.PageableUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

        private static final Set<String> SORT_FIELDS = Set.of(
                        "id", "title", "platform", "visibility", "displayOrder", "startDate", "endDate",
                        "createdAt", "updatedAt");

        private final BannerService bannerService;
        private final MessageService messageService;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<BannerResponse>>> getActiveBanners(
                        @RequestParam(value = "platform", required = false) String platform,
                        @PageableDefault(size = 10, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable requestedPageable) {
                Pageable pageable = PageableUtils.sanitize(
                                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.ASC, "displayOrder"));
                return ResponseEntity.ok(ApiResponse.success(bannerService.getActiveBanners(platform, pageable),
                                messageService.getMessage("banner.list.success")));
        }

        @GetMapping("/all")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Page<BannerResponse>>> getAllBanners(
                        @RequestParam(value = "platform", required = false) String platform,
                        @PageableDefault(size = 10, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable requestedPageable) {

                Pageable pageable = PageableUtils.sanitize(
                                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.ASC, "displayOrder"));

                return ResponseEntity.ok(
                                ApiResponse.success(bannerService.getAllBanners(platform, pageable),
                                                messageService.getMessage("banner.list.success")));
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<BannerResponse>> createBanner(
                        @RequestPart("image") MultipartFile image,
                        @ModelAttribute @Valid BannerRequest request) {

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.success(
                                                bannerService.createBanner(request, image),
                                                messageService.getMessage("banner.create.success")));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<BannerResponse>> updateBanner(
                        @PathVariable Long id,
                        @RequestPart(value = "image", required = false) MultipartFile image,
                        @ModelAttribute @Valid BannerRequest request) {
                UpdateResult<BannerResponse> updateResult = bannerService.updateBanner(id, request, image);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                updateResult.getResult(),
                                                messageService.getMessage(updateResult.getMessageKey())));
        }

        @PatchMapping("/reorder")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> reorderBanners(@RequestBody List<Long> orderedIds) {
                bannerService.reorderBanners(orderedIds);
                return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("banner.update.success")));
        }

        @PatchMapping("/{id}/toggle")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<BannerResponse>> toggleBanner(@PathVariable Long id) {
                UpdateResult<BannerResponse> updateResult = bannerService.toggleBanner(id);
                return ResponseEntity.ok(ApiResponse.success(updateResult.getResult(),
                                messageService.getMessage(updateResult.getMessageKey())));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
                bannerService.deleteBanner(id);
                return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("banner.delete.success")));
        }
}
