package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Banner;
import com.kindergarten.warehouse.dto.response.BannerResponse;
import com.kindergarten.warehouse.repository.BannerRepository;
import com.kindergarten.warehouse.service.BannerService;
import com.kindergarten.warehouse.service.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final MinioStorageService minioStorageService;

    public BannerServiceImpl(BannerRepository bannerRepository, MinioStorageService minioStorageService) {
        this.bannerRepository = bannerRepository;
        this.minioStorageService = minioStorageService;
    }

    @Override
    public List<BannerResponse> getActiveBanners() {
        return bannerRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<BannerResponse> getAllBanners() {
        return bannerRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public BannerResponse createBanner(MultipartFile image, String link, Integer order) {
        String imageUrl = minioStorageService.uploadFile(image, "banners");

        Banner banner = new Banner();
        banner.setImageUrl(imageUrl);
        banner.setLink(link);
        banner.setDisplayOrder(order);
        banner.setIsActive(true);

        return mapToResponse(bannerRepository.save(banner));
    }

    @Override
    public BannerResponse toggleBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.BANNER_NOT_FOUND));
        banner.setIsActive(!banner.getIsActive());
        return mapToResponse(bannerRepository.save(banner));
    }

    private BannerResponse mapToResponse(Banner banner) {
        return BannerResponse.builder()
                .id(banner.getId())
                .imageUrl(banner.getImageUrl())
                .link(banner.getLink())
                .isActive(banner.getIsActive())
                .order(banner.getDisplayOrder())
                .build();
    }

    @Override
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.BANNER_NOT_FOUND));

        // Delete image from MinIO
        minioStorageService.deleteFile(banner.getImageUrl());

        bannerRepository.deleteById(id);
    }
}
