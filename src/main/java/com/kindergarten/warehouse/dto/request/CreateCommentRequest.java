package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body cho tạo bình luận/đánh giá. Theo API_CONTRACT_V1 §2.1: chuyển từ
 * {@code @RequestParam} sang {@code @RequestBody} JSON.
 */
@Data
public class CreateCommentRequest {

    @NotBlank(message = "{validation.required}")
    private String resourceId;

    @NotBlank(message = "{validation.required}")
    @Size(max = 2000, message = "{validation.size}")
    private String content;

    // Cho phép null khi client bỏ qua -> mặc định 5 ở controller.
    @Min(value = 1, message = "{validation.size}")
    @Max(value = 5, message = "{validation.size}")
    private Integer rating;
}
