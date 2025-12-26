package com.kindergarten.warehouse.dto.request;

import lombok.Data;

public class ResourceFilterRequest {
    private String keyword;
    private Long topicId;
    private Long categoryId;
    private Long ageGroupId;
    private String topicSlug;
    private String categorySlug;
    private java.util.List<String> ageSlugs;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getAgeGroupId() {
        return ageGroupId;
    }

    public void setAgeGroupId(Long ageGroupId) {
        this.ageGroupId = ageGroupId;
    }

    public String getTopicSlug() {
        return topicSlug;
    }

    public void setTopicSlug(String topicSlug) {
        this.topicSlug = topicSlug;
    }

    public String getCategorySlug() {
        return categorySlug;
    }

    public void setCategorySlug(String categorySlug) {
        this.categorySlug = categorySlug;
    }

    public java.util.List<String> getAgeSlugs() {
        return ageSlugs;
    }

    public void setAgeSlugs(java.util.List<String> ageSlugs) {
        this.ageSlugs = ageSlugs;
    }
}
