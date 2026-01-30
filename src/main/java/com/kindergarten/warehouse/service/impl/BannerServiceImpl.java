package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.dto.request.BannerRequest;
import com.kindergarten.warehouse.dto.response.BannerResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.entity.Banner;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.BannerMapper;
import com.kindergarten.warehouse.repository.BannerRepository;
import com.kindergarten.warehouse.service.BannerService;
import com.kindergarten.warehouse.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final MinioStorageService minioStorageService;
    private final BannerMapper bannerMapper;

    @Override
    public List<BannerResponse> getActiveBanners(String platform) {
        return bannerRepository.findActiveBanners(platform, LocalDateTime.now()).stream()
                .map(bannerMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<BannerResponse> getAllBanners(Pageable pageable) {
        return bannerRepository.findAllByIsDeletedFalse(pageable)
                .map(bannerMapper::toResponse);
    }

    @Override
    public BannerResponse createBanner(BannerRequest request, MultipartFile image) {
        // Validate date range
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        String imageUrl = minioStorageService.uploadFile(image, "banners");

        Banner banner = new Banner();
        banner.setTitle(request.getTitle());
        banner.setSubtitle(request.getSubtitle());
        banner.setImageUrl(imageUrl);
        banner.setBgFrom(request.getBgFrom());
        banner.setBgTo(request.getBgTo());
        banner.setPlatform(request.getPlatform());
        banner.setLink(request.getLink());
        banner.setStartDate(request.getStartDate());
        banner.setEndDate(request.getEndDate());
        banner.setDisplayOrder(request.getDisplayOrder());
        banner.setIsActive(true);
        banner.setIsDeleted(false);

        return bannerMapper.toResponse(bannerRepository.save(banner));
    }

    @Override
    public UpdateResult<BannerResponse> updateBanner(Long id, BannerRequest request, MultipartFile image) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BANNER_NOT_FOUND));

        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        if (image != null && !image.isEmpty()) {
            if (banner.getImageUrl() != null && !banner.getImageUrl().isEmpty()) {
                try {
                    minioStorageService.deleteFile(banner.getImageUrl());
                } catch (Exception e) {
                    // Log warning
                }
            }
            String imageUrl = minioStorageService.uploadFile(image, "banners");
            banner.setImageUrl(imageUrl);
        }

        banner.setTitle(request.getTitle());
        banner.setSubtitle(request.getSubtitle());
        banner.setBgFrom(request.getBgFrom());
        banner.setBgTo(request.getBgTo());
        banner.setPlatform(request.getPlatform());
        banner.setLink(request.getLink());
        banner.setStartDate(request.getStartDate());
        banner.setEndDate(request.getEndDate());
        if (request.getDisplayOrder() != null) {
            banner.setDisplayOrder(request.getDisplayOrder());
        }

        return new UpdateResult<>(
                bannerMapper.toResponse(bannerRepository.save(banner)),
                "banner.update.success");
    }

    @Override
    public void reorderBanners(List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            Banner banner = bannerRepository.findById(id).orElse(null);
            if (banner != null) {
                banner.setDisplayOrder(i + 1);
                bannerRepository.save(banner);
            }
        }
    }

    @Override
    public UpdateResult<BannerResponse> toggleBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BANNER_NOT_FOUND));

        boolean newStatus = !banner.getIsActive();
        banner.setIsActive(newStatus);

        String messageKey = newStatus ? "banner.activated" : "banner.deactivated";

        return new UpdateResult<>(
                bannerMapper.toResponse(bannerRepository.save(banner)),
                messageKey);
    }

    @Override
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BANNER_NOT_FOUND));

        // Soft delete: keep image, just mark as deleted
        banner.setIsDeleted(true);
        banner.setIsActive(false); // Also deactivate it
        bannerRepository.save(banner);
    }
}
