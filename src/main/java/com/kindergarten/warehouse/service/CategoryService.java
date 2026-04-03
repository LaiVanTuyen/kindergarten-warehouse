package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.CategoryRequest;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import java.util.List;

import com.kindergarten.warehouse.dto.wrapper.UpdateResult;

public interface CategoryService {
        org.springframework.data.domain.Page<CategoryResponse> getAllCategories(boolean deleted, String keyword,
                        org.springframework.data.domain.Pageable pageable);

        CategoryResponse createCategory(CategoryRequest categoryRequest,
                        org.springframework.web.multipart.MultipartFile icon);

        UpdateResult<CategoryResponse> updateCategory(Long id, CategoryRequest categoryRequest,
                        org.springframework.web.multipart.MultipartFile icon);

        void deleteCategory(Long id, boolean hard);

        void deleteCategories(List<Long> ids, boolean hard);

        CategoryResponse restoreCategory(Long id);

        void restoreCategories(List<Long> ids);
}
