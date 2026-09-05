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

        /**
         * API_CONTRACT_V2 §0.5: whitelist sort RIENG cua endpoint nay.
         * KHONG co {@code topicCount} — no khong con la cot sau khi bo {@code @Formula}
         * (xem PERF_BASELINE §4). Sort theo no se tra 400, dung nhu tai lieu ghi.
         */
        private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of(
                        "id", "name", "slug", "displayOrder", "visibility", "createdAt", "updatedAt");

        private static final org.springframework.data.domain.Sort DEFAULT_SORT =
                        org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "id");

        private final CategoryService categoryService;
        private final MessageService messageService;
        private final com.kindergarten.warehouse.security.ViewerResolver viewerResolver;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getAllCategories(
                        @RequestParam(defaultValue = "false") boolean deleted,
                        @RequestParam(required = false) String keyword,
                        @org.springframework.data.web.PageableDefault(size = 10, sort = "id",
                                        direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable requestedPageable,
                        org.springframework.security.core.Authentication authentication) {

                // Bo cach chua cu "neu sortBy la asc/desc thi coi nhu id". No sinh ra tu
                // viec BE doc sortBy/sortDir con FE gui `sort=field,dir` (contract §0.5),
                // nen tham so lech nhau mot nac. Nay BE doc dung `sort` qua Pageable.
                Pageable pageable = PageableUtils.sanitize(requestedPageable, SORT_FIELDS, DEFAULT_SORT);

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
