package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.dto.request.CategoryRequest;
import com.kindergarten.warehouse.dto.response.CategoryResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.CategoryMapper;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.service.CategoryService;
import com.kindergarten.warehouse.service.MinioStorageService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final MinioStorageService minioStorageService;
    private final CategoryMapper categoryMapper;

    @Override
    public Page<CategoryResponse> getAllCategories(boolean deleted, String keyword, Pageable pageable) {
        Specification<Category> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Deleted Filter
            predicates.add(cb.equal(root.get("isDeleted"), deleted));

            // Keyword Search
            if (keyword != null && !keyword.isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern),
                        cb.like(cb.lower(root.get("description")), likePattern),
                        cb.like(cb.lower(root.get("slug")), likePattern)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return categoryRepository.findAll(spec, pageable).map(categoryMapper::toResponse);
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest categoryRequest, MultipartFile icon) {
        // Proactive validation for better UX
        if (categoryRepository.existsBySlugAndIsDeletedFalse(categoryRequest.getSlug())) {
            throw new AppException(ErrorCode.DUPLICATE_SLUG);
        }
        if (categoryRepository.existsByNameAndIsDeletedFalse(categoryRequest.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_NAME);
        }

        Category category = new Category();
        category.setName(categoryRequest.getName());
        category.setSlug(categoryRequest.getSlug());
        category.setDescription(categoryRequest.getDescription());
        if (categoryRequest.getIsActive() != null) {
            category.setIsActive(categoryRequest.getIsActive());
        }

        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioStorageService.uploadFile(icon, "categories");
            category.setIcon(iconUrl);
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    public UpdateResult<CategoryResponse> updateCategory(Long id, CategoryRequest categoryRequest, MultipartFile icon) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        // Proactive validation: check if slug/name changed and conflicts with existing
        if (!category.getSlug().equals(categoryRequest.getSlug())
                && categoryRepository.existsBySlugAndIsDeletedFalse(categoryRequest.getSlug())) {
            throw new AppException(ErrorCode.DUPLICATE_SLUG);
        }
        if (!category.getName().equals(categoryRequest.getName())
                && categoryRepository.existsByNameAndIsDeletedFalse(categoryRequest.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_NAME);
        }

        // Determine message key based on status change
        String messageKey = "category.update.success";
        if (categoryRequest.getIsActive() != null && !categoryRequest.getIsActive().equals(category.getIsActive())) {
            if (categoryRequest.getIsActive()) {
                messageKey = "category.activated";
            } else {
                messageKey = "category.deactivated";
            }
        }

        category.setName(categoryRequest.getName());
        category.setSlug(categoryRequest.getSlug());
        category.setDescription(categoryRequest.getDescription());
        if (categoryRequest.getIsActive() != null) {
            category.setIsActive(categoryRequest.getIsActive());
        }

        if (icon != null && !icon.isEmpty()) {
            if (category.getIcon() != null && !category.getIcon().isEmpty()) {
                try {
                    minioStorageService.deleteFile(category.getIcon());
                } catch (Exception e) {
                    // Log warning but proceed
                }
            }
            String iconUrl = minioStorageService.uploadFile(icon, "categories");
            category.setIcon(iconUrl);
        }

        return new UpdateResult<>(
                categoryMapper.toResponse(categoryRepository.save(category)),
                messageKey);
    }

    @Override
    public void deleteCategory(Long id, boolean hard) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (hard) {
            categoryRepository.delete(category);
        } else {
            category.setIsDeleted(true);
            categoryRepository.save(category);
        }
    }

    @Override
    public CategoryResponse restoreCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        category.setIsDeleted(false);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    private static final int BATCH_SIZE = 100;

    @Override
    public void deleteCategories(List<Long> ids, boolean hard) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        // Chunking to avoid database limits
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + BATCH_SIZE));

            if (hard) {
                // 1. Fetch images to delete (Projection for performance)
                List<com.kindergarten.warehouse.repository.projection.CategoryIconProjection> projections = categoryRepository
                        .findAllProjectedByIdIn(chunk);

                // 2. Parallel Delete Images from MinIO
                projections.parallelStream().forEach(p -> {
                    if (p.getIcon() != null && !p.getIcon().isEmpty()) {
                        try {
                            minioStorageService.deleteFile(p.getIcon());
                        } catch (Exception e) {
                            // Log and continue
                        }
                    }
                });

                // 3. Hard Delete from DB (1 Query)
                categoryRepository.hardDeleteAllByIds(chunk);
            } else {
                // Soft Delete (1 Query)
                categoryRepository.softDeleteAllByIds(chunk);
            }
        }
    }

    @Override
    public void restoreCategories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + BATCH_SIZE));
            categoryRepository.restoreAllByIds(chunk);
        }
    }
}
