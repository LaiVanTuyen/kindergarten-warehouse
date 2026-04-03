package com.kindergarten.warehouse.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

        private final CategoryService categoryService;
        private final MessageService messageService;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getAllCategories(
                        @RequestParam(defaultValue = "false") boolean deleted,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "id") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortDir) {

                // Fix FE sending 'desc' as sortBy causing PropertyReferenceException
                if ("desc".equalsIgnoreCase(sortBy) || "asc".equalsIgnoreCase(sortBy)) {
                        sortBy = "id";
                }

                Pageable pageable = PageableUtils.createPageable(page, size, sortBy, sortDir);

                return ResponseEntity
                                .ok(ApiResponse.success(categoryService.getAllCategories(deleted, keyword, pageable),
                                                messageService.getMessage("category.list.success")));
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
                        @RequestPart(value = "icon", required = false) MultipartFile icon,
                        @ModelAttribute @Valid CategoryRequest categoryRequest) {
                return new ResponseEntity<>(
                                ApiResponse.success(categoryService.createCategory(categoryRequest, icon),
                                                messageService.getMessage("category.create.success")),
                                HttpStatus.CREATED);
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

        @DeleteMapping("/bulk")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> deleteCategories(
                        @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 1000, message = "{validation.size}") java.util.List<Long> ids,
                        @RequestParam(defaultValue = "false") boolean hard) {
                categoryService.deleteCategories(ids, hard);
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
                        @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 1000, message = "{validation.size}") java.util.List<Long> ids) {
                categoryService.restoreCategories(ids);
                return ResponseEntity.ok(ApiResponse.success(null,
                                messageService.getMessage("category.restore.bulk.success")));
        }
}
