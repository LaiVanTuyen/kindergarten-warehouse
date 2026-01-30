package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Setter
@Getter
public class BannerResponse extends BaseResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String bgFrom;
    private String bgTo;
    private String platform;
    private String link;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;
    private Integer displayOrder;
    // auditing fields moved to BaseResponse

    public BannerResponse() {
        super();
    }

    public BannerResponse(Long id, String title, String subtitle, String imageUrl, String bgFrom, String bgTo,
            String platform,
            String link, LocalDateTime startDate, LocalDateTime endDate, Boolean isActive, Integer displayOrder,
            LocalDateTime createdAt, LocalDateTime updatedAt, String createdBy, String updatedBy) {
        super(createdAt, updatedAt, createdBy, updatedBy);
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.imageUrl = imageUrl;
        this.bgFrom = bgFrom;
        this.bgTo = bgTo;
        this.platform = platform;
        this.link = link;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
    }

    public static BannerResponseBuilder builder() {
        return new BannerResponseBuilder();
    }

    public static class BannerResponseBuilder {
        private Long id;
        private String title;
        private String subtitle;
        private String imageUrl;
        private String bgFrom;
        private String bgTo;
        private String platform;
        private String link;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Boolean isActive;
        private Integer displayOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        BannerResponseBuilder() {
        }

        public BannerResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public BannerResponseBuilder title(String title) {
            this.title = title;
            return this;
        }

        public BannerResponseBuilder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public BannerResponseBuilder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public BannerResponseBuilder bgFrom(String bgFrom) {
            this.bgFrom = bgFrom;
            return this;
        }

        public BannerResponseBuilder bgTo(String bgTo) {
            this.bgTo = bgTo;
            return this;
        }

        public BannerResponseBuilder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public BannerResponseBuilder link(String link) {
            this.link = link;
            return this;
        }

        public BannerResponseBuilder startDate(LocalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public BannerResponseBuilder endDate(LocalDateTime endDate) {
            this.endDate = endDate;
            return this;
        }

        public BannerResponseBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public BannerResponseBuilder displayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
            return this;
        }

        public BannerResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public BannerResponseBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public BannerResponseBuilder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public BannerResponseBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public BannerResponse build() {
            return new BannerResponse(id, title, subtitle, imageUrl, bgFrom, bgTo, platform, link, startDate, endDate,
                    isActive, displayOrder, createdAt, updatedAt, createdBy, updatedBy);
        }
    }
}
