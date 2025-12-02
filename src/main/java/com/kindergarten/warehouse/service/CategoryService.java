package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.CategoryRequest;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {
        List<CategoryResponse> getAllCategories();

        CategoryResponse createCategory(CategoryRequest categoryRequest);

        CategoryResponse updateCategory(Long id, CategoryRequest categoryRequest);

        void deleteCategory(Long id);
}
