package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> getAllCategories();

    Category createCategory(Category category);

    Category updateCategory(Long id, Category categoryDetails);

    void deleteCategory(Long id);
}
