package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

public class BannerResponse {
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

    public BannerResponse() {
    }

    public BannerResponse(Long id, String title, String subtitle, String imageUrl, String bgFrom, String bgTo,
            String platform,
            String link, LocalDateTime startDate, LocalDateTime endDate, Boolean isActive, Integer displayOrder,
            LocalDateTime createdAt, LocalDateTime updatedAt, String createdBy, String updatedBy) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public static BannerResponseBuilder builder() {
        return new BannerResponseBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBgFrom() {
        return bgFrom;
    }

    public void setBgFrom(String bgFrom) {
        this.bgFrom = bgFrom;
    }

    public String getBgTo() {
        return bgTo;
    }

    public void setBgTo(String bgTo) {
        this.bgTo = bgTo;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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
