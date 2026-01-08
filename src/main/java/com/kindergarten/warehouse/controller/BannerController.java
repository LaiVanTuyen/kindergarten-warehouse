package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.BannerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.BannerResponse;
import com.kindergarten.warehouse.dto.request.BannerRequest;
import jakarta.validation.Valid;

import com.kindergarten.warehouse.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/v1/banners")
public class BannerController {

    private final BannerService bannerService;
    private final MessageService messageService;

    public BannerController(BannerService bannerService, MessageService messageService) {
        this.bannerService = bannerService;
        this.messageService = messageService;
    }

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
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

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

        return ResponseEntity.ok(
                ApiResponse.success(
                        bannerService.updateBanner(id, request, image),
                        messageService.getMessage("banner.update.success")));
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
        return ResponseEntity
                .ok(ApiResponse.success(bannerService.toggleBanner(id),
                        messageService.getMessage("banner.toggle.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("banner.delete.success")));
    }
}
