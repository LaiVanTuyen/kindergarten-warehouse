package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.Banner;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface BannerService {
    List<Banner> getActiveBanners();

    List<Banner> getAllBanners();

    Banner createBanner(MultipartFile image, String link, Integer order);

    Banner toggleBanner(Long id);

    void deleteBanner(Long id);
}
