package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.BulkLongIdsRequest;
import com.kindergarten.warehouse.dto.request.CategoryRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.service.CategoryService;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.util.PageableUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

        private static final Set<String> SORT_FIELDS = Set.of(
                        "id", "name", "slug", "visibility", "createdAt", "updatedAt");

        private final CategoryService categoryService;
        private final MessageService messageService;
        private final com.kindergarten.warehouse.security.ViewerResolver viewerResolver;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getAllCategories(
                        @RequestParam(defaultValue = "false") boolean deleted,
                        @RequestParam(required = false) String keyword,
                        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable requestedPageable,
                        org.springframework.security.core.Authentication authentication) {

                Pageable pageable = PageableUtils.sanitize(
                                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "id"));

                return ResponseEntity
                                .ok(ApiResponse.success(categoryService.getAllCategories(
                                                deleted, keyword, pageable, viewerResolver.resolve(authentication)),
                                                messageService.getMessage("category.list.success")));
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
                        @RequestPart(value = "icon", required = false) MultipartFile icon,
                        @ModelAttribute @Valid CategoryRequest categoryRequest) {
                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.success(categoryService.createCategory(categoryRequest, icon),
                                                messageService.getMessage("category.create.success")));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryJson(
                        @PathVariable Long id,
                        @RequestBody @Valid CategoryRequest categoryRequest) {
                UpdateResult<CategoryResponse> updateResult = categoryService.updateCategory(id, categoryRequest, null);
                return ResponseEntity.ok(ApiResponse.success(updateResult.getResult(),
                                messageService.getMessage(updateResult.getMessageKey())));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
                        @PathVariable Long id,
                        @RequestPart(value = "icon", required = false) MultipartFile icon,
                        @ModelAttribute @Valid CategoryRequest categoryRequest) {
                UpdateResult<CategoryResponse> updateResult = categoryService.updateCategory(id, categoryRequest, icon);
                return ResponseEntity.ok(ApiResponse.success(updateResult.getResult(),
                                messageService.getMessage(updateResult.getMessageKey())));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id,
                        @RequestParam(defaultValue = "false") boolean hard) {
                categoryService.deleteCategory(id, hard);
                return ResponseEntity
                                .ok(ApiResponse.success(null, messageService.getMessage("category.delete.success")));
        }

        @PostMapping("/bulk-delete")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> deleteCategories(
                        @Valid @RequestBody BulkLongIdsRequest request,
                        @RequestParam(defaultValue = "false") boolean hard) {
                categoryService.deleteCategories(request.getIds(), hard);
                return ResponseEntity.ok(ApiResponse.success(null,
                                messageService.getMessage("category.delete.bulk.success")));
        }

        @PatchMapping("/{id}/restore")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<CategoryResponse>> restoreCategory(@PathVariable Long id) {
                return ResponseEntity.ok(ApiResponse.success(categoryService.restoreCategory(id),
                                messageService.getMessage("category.restore.success")));
        }

        @PatchMapping("/bulk-restore")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> restoreCategories(
                        @Valid @RequestBody BulkLongIdsRequest request) {
                categoryService.restoreCategories(request.getIds());
                return ResponseEntity.ok(ApiResponse.success(null,
                                messageService.getMessage("category.restore.bulk.success")));
        }
}
