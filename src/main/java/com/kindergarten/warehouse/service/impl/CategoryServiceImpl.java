package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.dto.request.CategoryRequest;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.service.CategoryService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final MessageSource messageSource;

    public CategoryServiceImpl(CategoryRepository categoryRepository, MessageSource messageSource) {
        this.categoryRepository = categoryRepository;
        this.messageSource = messageSource;
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
                .orElseThrow(() -> new RuntimeException(
                        messageSource.getMessage("error.category.not_found", null, LocaleContextHolder.getLocale())));

        category.setName(categoryRequest.getName());
        category.setSlug(categoryRequest.getSlug());

        return mapToResponse(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) {
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
