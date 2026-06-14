package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.Visibility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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
    private Visibility visibility;
    private Integer displayOrder;
}
