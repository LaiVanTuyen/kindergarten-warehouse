package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BannerResponse {
    private Long id;
    private String imageUrl;
    private String link;
    private Boolean isActive;
    private Integer order;
}
