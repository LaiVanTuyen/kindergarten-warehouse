package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.BannerResponse;
import com.kindergarten.warehouse.entity.Banner;
import org.springframework.stereotype.Component;

@Component
public class BannerMapper {

    public BannerResponse toResponse(Banner banner) {
        if (banner == null) {
            return null;
        }

        return BannerResponse.builder()
                .id(banner.getId())
                .title(banner.getTitle())
                .subtitle(banner.getSubtitle())
                .imageUrl(banner.getImageUrl())
                .bgFrom(banner.getBgFrom())
                .bgTo(banner.getBgTo())
                .platform(banner.getPlatform())
                .link(banner.getLink())
                .startDate(banner.getStartDate())
                .endDate(banner.getEndDate())
                .isActive(banner.getIsActive())
                .displayOrder(banner.getDisplayOrder())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .createdBy(banner.getCreator() != null ? banner.getCreator().getFullName() : null)
                .updatedBy(banner.getUpdater() != null ? banner.getUpdater().getFullName() : null)
                .build();
    }
}
