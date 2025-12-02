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

import com.kindergarten.warehouse.service.MessageService;

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
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getActiveBanners() {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getActiveBanners(),
                messageService.getMessage("banner.list.success")));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getAllBanners() {
        return ResponseEntity.ok(
                ApiResponse.success(bannerService.getAllBanners(), messageService.getMessage("banner.list.success")));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "link", required = false) String link,
            @RequestParam(value = "order", defaultValue = "0") Integer order) {

        return new ResponseEntity<>(
                ApiResponse.success(bannerService.createBanner(image, link, order),
                        messageService.getMessage("banner.create.success")),
                HttpStatus.CREATED);
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
