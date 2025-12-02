package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.dto.request.CategoryRequest;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        Category category = new Category();
        category.setName(categoryRequest.getName());
        category.setSlug(categoryRequest.getSlug());
        return mapToResponse(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse updateCategory(Long id, CategoryRequest categoryRequest) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.CATEGORY_NOT_FOUND));

        category.setName(categoryRequest.getName());
        category.setSlug(categoryRequest.getSlug());

        return mapToResponse(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.CATEGORY_NOT_FOUND);
        }
        categoryRepository.deleteById(id);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .build();
    }
}
