package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.BannerResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface BannerService {
    List<BannerResponse> getActiveBanners();

    List<BannerResponse> getAllBanners();

    BannerResponse createBanner(MultipartFile image, String link, Integer order);

    BannerResponse toggleBanner(Long id);

    void deleteBanner(Long id);
}
