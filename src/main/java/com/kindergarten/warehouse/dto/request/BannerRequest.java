package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BannerRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 255, message = "Subtitle must not exceed 255 characters")
    private String subtitle;

    @NotBlank(message = "Background From color is required")
    private String bgFrom;

    @NotBlank(message = "Background To color is required")
    private String bgTo;

    private String platform = "WEB";

    @Size(max = 500, message = "Link must not exceed 500 characters")
    private String link;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Integer displayOrder = 0;
}
