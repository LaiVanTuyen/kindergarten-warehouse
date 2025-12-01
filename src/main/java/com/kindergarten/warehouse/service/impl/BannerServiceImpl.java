package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Banner;
import com.kindergarten.warehouse.repository.BannerRepository;
import com.kindergarten.warehouse.service.BannerService;
import com.kindergarten.warehouse.service.FirebaseService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final FirebaseService firebaseService;
    private final MessageSource messageSource;

    public BannerServiceImpl(BannerRepository bannerRepository, FirebaseService firebaseService,
            MessageSource messageSource) {
        this.bannerRepository = bannerRepository;
        this.firebaseService = firebaseService;
        this.messageSource = messageSource;
    }

    @Override
    public List<Banner> getActiveBanners() {
        return bannerRepository.findByIsActiveTrueOrderByOrderAsc();
    }

    @Override
    public List<Banner> getAllBanners() {
        return bannerRepository.findAll();
    }

    @Override
    public Banner createBanner(MultipartFile image, String link, Integer order) {
        try {
            String imageUrl = firebaseService.uploadFile(image);

            Banner banner = new Banner();
            banner.setImageUrl(imageUrl);
            banner.setLink(link);
            banner.setOrder(order);
            banner.setIsActive(true);

            return bannerRepository.save(banner);
        } catch (IOException e) {
            throw new RuntimeException(
                    messageSource.getMessage("error.firebase.init", null, LocaleContextHolder.getLocale()), e);
        }
    }

    @Override
    public Banner toggleBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        messageSource.getMessage("error.banner.not_found", null, LocaleContextHolder.getLocale())));
        banner.setIsActive(!banner.getIsActive());
        return bannerRepository.save(banner);
    }

    @Override
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        messageSource.getMessage("error.banner.not_found", null, LocaleContextHolder.getLocale())));

        // Optionally delete image from Firebase
        firebaseService.deleteFile(banner.getImageUrl());

        bannerRepository.deleteById(id);
    }
}
