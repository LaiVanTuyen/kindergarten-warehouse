package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import com.kindergarten.warehouse.dto.request.CategoryRequest;

import com.kindergarten.warehouse.service.MessageService;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final MessageService messageService;

    public CategoryController(CategoryService categoryService, MessageService messageService) {
        this.categoryService = categoryService;
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategories(),
                messageService.getMessage("category.list.success")));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @jakarta.validation.Valid CategoryRequest categoryRequest) {
        return new ResponseEntity<>(
                ApiResponse.success(categoryService.createCategory(categoryRequest),
                        messageService.getMessage("category.create.success")),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @RequestBody @jakarta.validation.Valid CategoryRequest categoryRequest) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, categoryRequest),
                messageService.getMessage("category.update.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("category.delete.success")));
    }
}
