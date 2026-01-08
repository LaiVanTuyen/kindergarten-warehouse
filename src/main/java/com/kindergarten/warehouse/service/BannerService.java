package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.BannerResponse;
import com.kindergarten.warehouse.dto.request.BannerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface BannerService {
    List<BannerResponse> getActiveBanners(String platform);

    Page<BannerResponse> getAllBanners(Pageable pageable);

    BannerResponse createBanner(BannerRequest request, MultipartFile image);

    BannerResponse updateBanner(Long id, BannerRequest request, MultipartFile image);

    void reorderBanners(List<Long> orderedIds);

    BannerResponse toggleBanner(Long id);

    void deleteBanner(Long id);
}
