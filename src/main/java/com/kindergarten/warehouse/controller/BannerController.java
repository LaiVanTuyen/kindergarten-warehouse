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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

        private final BannerService bannerService;
        private final MessageService messageService;

        @GetMapping
        public ResponseEntity<ApiResponse<List<BannerResponse>>> getActiveBanners(
                        @RequestParam(value = "platform", required = false) String platform) {
                return ResponseEntity.ok(ApiResponse.success(bannerService.getActiveBanners(platform),
                                messageService.getMessage("banner.list.success")));
        }

        @GetMapping("/all")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Page<BannerResponse>>> getAllBanners(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "displayOrder") String sortBy,
                        @RequestParam(defaultValue = "asc") String sortDir) {

                Pageable pageable = PageableUtils.createPageable(page, size, sortBy, sortDir);

                return ResponseEntity.ok(
                                ApiResponse.success(bannerService.getAllBanners(pageable),
                                                messageService.getMessage("banner.list.success")));
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<BannerResponse>> createBanner(
                        @RequestPart("image") MultipartFile image,
                        @ModelAttribute @Valid BannerRequest request) {

                return new ResponseEntity<>(
                                ApiResponse.success(
                                                bannerService.createBanner(request, image),
                                                messageService.getMessage("banner.create.success")),
                                HttpStatus.CREATED);
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

        @PutMapping("/{id}/toggle")
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
