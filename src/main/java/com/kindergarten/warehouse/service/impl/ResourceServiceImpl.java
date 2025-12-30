package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.FileType;
import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.ResourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResourceServiceImpl implements ResourceService {

    // Map to store IP_ResourceID -> LastViewTimestamp
    private final java.util.Map<String, Long> viewTracker = new java.util.concurrent.ConcurrentHashMap<>();

    private final ResourceRepository resourceRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final com.kindergarten.warehouse.repository.AgeGroupRepository ageGroupRepository;
    private final MinioStorageService minioStorageService;
    private final com.kindergarten.warehouse.repository.FavoriteRepository favoriteRepository;
    private final com.kindergarten.warehouse.repository.CommentRepository commentRepository;

    public ResourceServiceImpl(ResourceRepository resourceRepository, TopicRepository topicRepository,
            UserRepository userRepository, com.kindergarten.warehouse.repository.AgeGroupRepository ageGroupRepository,
            MinioStorageService minioStorageService,
            com.kindergarten.warehouse.repository.FavoriteRepository favoriteRepository,
            com.kindergarten.warehouse.repository.CommentRepository commentRepository) {
        this.resourceRepository = resourceRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.ageGroupRepository = ageGroupRepository;
        this.minioStorageService = minioStorageService;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
    }

    @Override
    @LogAction(action = "CREATE", description = "Uploaded resource")
    public ResourceResponse uploadResource(MultipartFile file, String title,
            String description, Long topicId, java.util.List<Long> ageGroupIds, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.TOPIC_NOT_FOUND));

        java.util.Set<com.kindergarten.warehouse.entity.AgeGroup> ageGroups = new java.util.HashSet<>();
        if (ageGroupIds != null && !ageGroupIds.isEmpty()) {
            ageGroups.addAll(ageGroupRepository.findAllById(ageGroupIds));
        }

        String fileUrl = minioStorageService.uploadFile(file);
        String extension = getExtension(file.getOriginalFilename());
        FileType type = determineFileType(extension);

        Resource resource = new Resource();
        resource.setTitle(title);
        // Generate slug from title + random/timestamp or just accept user input later.
        // For now, using title-timestamp for uniqueness.
        resource.setSlug(com.kindergarten.warehouse.util.SlugUtil.toSlug(title + "-" + System.currentTimeMillis()));
        resource.setDescription(description);
        resource.setTopic(topic);
        resource.setFileUrl(fileUrl);
        resource.setFileExtension(extension);
        resource.setFileType(type.name());
        resource.setFileSize(file.getSize());
        resource.setCreatedBy(user);
        resource.setAgeGroups(ageGroups);

        return mapToResponse(resourceRepository.save(resource));
    }

    @Override
    public Page<ResourceResponse> getResources(
            com.kindergarten.warehouse.dto.request.ResourceFilterRequest filterRequest, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        org.springframework.data.jpa.domain.Specification<Resource> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));

            if (filterRequest.getTopicSlug() != null && !filterRequest.getTopicSlug().isEmpty()) {
                predicates.add(cb.equal(root.get("topic").get("slug"), filterRequest.getTopicSlug()));
            } else if (filterRequest.getCategorySlug() != null && !filterRequest.getCategorySlug().isEmpty()) {
                predicates
                        .add(cb.equal(root.get("topic").get("category").get("slug"), filterRequest.getCategorySlug()));
            }

            if (filterRequest.getTopicId() != null) {
                predicates.add(cb.equal(root.get("topic").get("id"), filterRequest.getTopicId()));
            } else if (filterRequest.getCategoryId() != null) {
                // Assuming Topic has category field
                predicates.add(cb.equal(root.get("topic").get("category").get("id"), filterRequest.getCategoryId()));
            }

            if (filterRequest.getAgeGroupId() != null) {
                predicates.add(cb.equal(root.join("ageGroups").get("id"), filterRequest.getAgeGroupId()));
            }

            if (filterRequest.getAgeSlugs() != null && !filterRequest.getAgeSlugs().isEmpty()) {
                // Join with ageGroups and check if slug is in the list
                predicates.add(root.join("ageGroups").get("slug").in(filterRequest.getAgeSlugs()));
            }

            if (filterRequest.getKeyword() != null && !filterRequest.getKeyword().isEmpty()) {
                String likePattern = "%" + filterRequest.getKeyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), likePattern));
            }

            if (filterRequest.getStatus() != null && !filterRequest.getStatus().isEmpty()) {
                predicates.add(
                        cb.equal(root.get("status"),
                                com.kindergarten.warehouse.entity.ResourceStatus.valueOf(filterRequest.getStatus())));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Resource> resourcePage = resourceRepository.findAll(spec, pageable);
        return resourcePage.map(this::mapToResponse);
    }

    private ResourceResponse mapToResponse(Resource resource) {
        // Average rating is now fetched from cache column in Resource entity
        // Double averageRating =
        // commentRepository.getAverageRatingByResourceId(resource.getId());

        // Check isFavorited
        boolean isFavorited = false;
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            String username = authentication.getName();
            User currentUser = userRepository.findByUsername(username).orElse(null);
            if (currentUser != null) {
                isFavorited = favoriteRepository.existsByUserIdAndResourceId(currentUser.getId(), resource.getId());
            }
        }

        return ResourceResponse.builder()
                .id(resource.getId())
                .title(resource.getTitle())
                .slug(resource.getSlug())
                .description(resource.getDescription())
                .viewsCount(resource.getViewsCount())
                .createdAt(resource.getCreatedAt())
                .fileUrl(minioStorageService.getPresignedUrl(extractKeyFromUrl(resource.getFileUrl())))
                .thumbnailUrl(resource.getThumbnailUrl())
                .fileType(resource.getFileType())
                .fileExtension(resource.getFileExtension())
                .topicId(resource.getTopic().getId())
                .topicName(resource.getTopic().getName())
                .fileSize(resource.getFileSize())
                .createdBy(resource.getCreatedBy() != null ? resource.getCreatedBy().getFullName() : null)
                .ageGroups(resource.getAgeGroups().stream()
                        .map(ag -> com.kindergarten.warehouse.dto.response.AgeGroupResponse.builder()
                                .id(ag.getId())
                                .name(ag.getName())
                                .slug(ag.getSlug())
                                .minAge(ag.getMinAge())
                                .maxAge(ag.getMaxAge())
                                .description(ag.getDescription())
                                .build())
                        .collect(java.util.stream.Collectors.toList()))
                .highlights(resource.getHighlights())
                .isFavorited(isFavorited)
                .averageRating(resource.getAverageRating() != null ? resource.getAverageRating() : 0.0)
                .status(resource.getStatus().name())
                .downloadCount(resource.getDownloadCount())
                .build();
    }

    @Override
    public void incrementViewCount(String id, String ipAddress) {
        String key = ipAddress + "_" + id;
        long currentTime = System.currentTimeMillis();
        long oneHourInMillis = 3600000;

        if (viewTracker.containsKey(key)) {
            long lastViewTime = viewTracker.get(key);
            if (currentTime - lastViewTime < oneHourInMillis) {
                // Debounce: view already counted recently
                return;
            }
        }

        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND));
        resource.setViewsCount(resource.getViewsCount() + 1);
        resourceRepository.save(resource);

        viewTracker.put(key, currentTime);

        // Optional: Cleanup old entries logic could be added here or via scheduled task
        // but for simplicity/MVP we keep it simple.
    }

    @Override
    public void incrementDownloadCount(String id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND));
        resource.setDownloadCount(resource.getDownloadCount() + 1);
        resourceRepository.save(resource);
    }

    @Override
    @LogAction(action = "DELETE", description = "Deleted resource")
    public void deleteResource(String id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND));

        // Soft Delete
        resource.setIsDeleted(true);
        resourceRepository.save(resource);
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    @Override
    public ResourceResponse getResourceBySlug(String slug) {
        Resource resource = resourceRepository.findBySlug(slug)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND));

        // Logic check: only active/non-deleted? Repository findBySlug should probably
        // handle this or check here.
        if (resource.getIsDeleted()) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND);
        }

        return mapToResponse(resource);
    }

    private FileType determineFileType(String extension) {
        switch (extension) {
            case "mp4":
            case "mov":
            case "avi":
                return FileType.VIDEO;
            case "doc":
            case "docx":
                return FileType.DOCUMENT;
            case "xls":
            case "xlsx":
                return FileType.EXCEL;
            case "pdf":
                return FileType.PDF;
            default:
                return FileType.DOCUMENT; // Default fallback
        }
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("/")) {
            return fileUrl;
        }
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }
}
