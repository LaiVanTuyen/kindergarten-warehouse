package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.BannerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ResponseEntity<List<com.kindergarten.warehouse.dto.response.BannerResponse>> getActiveBanners() {
        return ResponseEntity.ok(bannerService.getActiveBanners());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<com.kindergarten.warehouse.dto.response.BannerResponse>> getAllBanners() {
        return ResponseEntity.ok(bannerService.getAllBanners());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<com.kindergarten.warehouse.dto.response.BannerResponse> createBanner(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "link", required = false) String link,
            @RequestParam(value = "order", defaultValue = "0") Integer order) {

        return new ResponseEntity<>(bannerService.createBanner(image, link, order), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<com.kindergarten.warehouse.dto.response.BannerResponse> toggleBanner(@PathVariable Long id) {
        return ResponseEntity.ok(bannerService.toggleBanner(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }
}
