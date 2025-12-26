package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

public class BannerResponse {
    private Long id;
    private String imageUrl;
    private String link;
    private Boolean isActive;
    private Integer order;

    public BannerResponse() {
    }

    public BannerResponse(Long id, String imageUrl, String link, Boolean isActive, Integer order) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.link = link;
        this.isActive = isActive;
        this.order = order;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public static class BannerResponseBuilder {
        private Long id;
        private String imageUrl;
        private String link;
        private Boolean isActive;
        private Integer order;

        BannerResponseBuilder() {
        }

        public BannerResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public BannerResponseBuilder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public BannerResponseBuilder link(String link) {
            this.link = link;
            return this;
        }

        public BannerResponseBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public BannerResponseBuilder order(Integer order) {
            this.order = order;
            return this;
        }

        public BannerResponse build() {
            return new BannerResponse(id, imageUrl, link, isActive, order);
        }
    }
}
