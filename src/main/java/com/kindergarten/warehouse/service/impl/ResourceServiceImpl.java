package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.BulkResourceRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.BulkOperationResponse;
import com.kindergarten.warehouse.dto.response.FileDownloadInfo;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.*;
import com.kindergarten.warehouse.event.ResourceRejectedEvent;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.*;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.ResourceService;
import com.kindergarten.warehouse.service.ResourceStatService;
import com.kindergarten.warehouse.service.YoutubeService;
import com.kindergarten.warehouse.util.AppConstants;
import com.kindergarten.warehouse.util.PageableUtils;
import com.kindergarten.warehouse.util.SlugUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final MinioStorageService minioStorageService;
    private final FavoriteRepository favoriteRepository;

    private final ResourceMapper resourceMapper;
    private final ResourceStatService resourceStatService;
    private final YoutubeService youtubeService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.kindergarten.warehouse.service.AuditLogService auditLogService;
    private final com.kindergarten.warehouse.security.ResourceAccessGuard resourceAccessGuard;
    private final com.kindergarten.warehouse.mapper.AgeGroupMapper ageGroupMapper;

    @Value("${app.resource.thumbnail-max-bytes:5242880}")
    private long thumbnailMaxBytes;

    @Override
    @Transactional
    @LogAction(action = AuditAction.CREATE, description = "Uploaded resource", target = "RESOURCE")
    public ResourceResponse uploadResource(ResourceCreationRequest request, String username) {
        if ((request.getFile() == null || request.getFile().isEmpty()) &&
                (request.getYoutubeLink() == null || request.getYoutubeLink().isEmpty())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getFile() != null && !request.getFile().isEmpty()
                && request.getYoutubeLink() != null && !request.getYoutubeLink().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Topic topic = getActiveTopicOrThrow(request.getTopicId());
        Set<AgeGroup> ageGroups = getAgeGroupsOrThrow(request.getAgeGroupIds());

        String fileUrl;
        ResourceType resourceType;
        String fileType;
        String extension;
        Long fileSize = 0L;
        String duration = request.getDuration();

        if (request.getYoutubeLink() != null && !request.getYoutubeLink().isEmpty()) {
            String youtubeId = youtubeService.extractVideoId(request.getYoutubeLink());
            if (youtubeId == null) {
                throw new AppException(ErrorCode.INVALID_YOUTUBE_LINK);
            }

            boolean isAccessible = true;
            try {
                isAccessible = youtubeService.isVideoAccessible(youtubeId);
            } catch (Exception e) {
                log.warn("Cannot verify YouTube video accessibility: {}", e.getMessage());
            }
            if (!isAccessible) {
                throw new AppException(ErrorCode.INVALID_YOUTUBE_LINK);
            }

            fileUrl = request.getYoutubeLink();
            resourceType = ResourceType.YOUTUBE;
            fileType = "VIDEO";
            extension = "youtube";

            if (duration == null || duration.isEmpty()) {
                try {
                    duration = youtubeService.getVideoDuration(youtubeId);
                } catch (Exception e) {
                    log.warn("Failed to fetch YouTube duration for video {}: {}", youtubeId, e.getMessage());
                    duration = "Unknown";
                }
            }
        } else {
            String path = AppConstants.BUCKET_RESOURCES + "/" + AppConstants.FOLDER_FILES;
            fileUrl = minioStorageService.uploadFile(request.getFile(), path);
            resourceType = ResourceType.FILE;
            extension = getExtension(request.getFile().getOriginalFilename());
            fileType = determineFileType(extension).name();
            fileSize = request.getFile().getSize();
        }

        String thumbnailUrl = null;
        if (request.getThumbnail() != null && !request.getThumbnail().isEmpty()) {
            String thumbnailContentType = request.getThumbnail().getContentType();
            if (!isValidImageFormat(thumbnailContentType)) {
                throw new AppException(ErrorCode.INVALID_IMAGE_FORMAT);
            }
            if (request.getThumbnail().getSize() > thumbnailMaxBytes) {
                throw new AppException(ErrorCode.THUMBNAIL_TOO_LARGE);
            }
            String path = AppConstants.BUCKET_RESOURCES + "/" + AppConstants.FOLDER_THUMBNAILS;
            thumbnailUrl = minioStorageService.uploadFile(request.getThumbnail(), path);
        } else if (resourceType == ResourceType.YOUTUBE) {
            String youtubeId = youtubeService.extractVideoId(fileUrl);
            if (youtubeId != null) {
                thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg";
            }
        }

        Resource resource = new Resource();
        resource.setTitle(request.getTitle());
        resource.setSlug(SlugUtil.toSlug(request.getTitle() + "-" + System.currentTimeMillis()));
        resource.setDescription(request.getDescription());
        resource.setTopic(topic);
        resource.setFileUrl(fileUrl);
        resource.setThumbnailUrl(thumbnailUrl);
        resource.setResourceType(resourceType);
        resource.setFileExtension(extension);
        resource.setFileType(fileType);
        resource.setFileSize(fileSize);
        if (duration != null && duration.length() > 20) {
            duration = duration.substring(0, 20);
        }
        resource.setDuration(duration);
        resource.setCreatedBy(user.getId());
        resource.setCreator(user);
        resource.setAgeGroups(ageGroups);

        boolean isAdmin = user.getRoles().stream().anyMatch(role -> role == Role.ADMIN);
        if (isAdmin) {
            resource.setStatus(ResourceStatus.APPROVED);
        } else {
            resource.setStatus(ResourceStatus.PENDING);
        }
        resource.setVisibility(Visibility.PUBLIC);
        resource.setIsDeleted(false);

        return resourceMapper.toResponse(resourceRepository.save(resource), false);
    }

    private Specification<Resource> createBaseSpecification(ResourceFilterRequest filterRequest) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Topic Filter (Multi-select)
            if (filterRequest.getTopicSlugs() != null && !filterRequest.getTopicSlugs().isEmpty()) {
                predicates.add(root.get("topic").get("slug").in(filterRequest.getTopicSlugs()));
            } else if (filterRequest.getCategorySlugs() != null && !filterRequest.getCategorySlugs().isEmpty()) {
                predicates.add(root.get("topic").get("category").get("slug").in(filterRequest.getCategorySlugs()));
            }

            // Legacy ID filters
            if (filterRequest.getTopicId() != null) {
                predicates.add(cb.equal(root.get("topic").get("id"), filterRequest.getTopicId()));
            } else if (filterRequest.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("topic").get("category").get("id"), filterRequest.getCategoryId()));
            }

            // Age Group Filter (Multi-select) using EXISTS Subquery (No DISTINCT, High
            // Performance)
            if (filterRequest.getAgeSlugs() != null && !filterRequest.getAgeSlugs().isEmpty()) {
                jakarta.persistence.criteria.Subquery<String> subquery = query.subquery(String.class);
                jakarta.persistence.criteria.Root<Resource> subRoot = subquery.correlate(root);
                jakarta.persistence.criteria.Join<Resource, AgeGroup> ageGroupJoin = subRoot.join("ageGroups");
                subquery.select(subRoot.get("id")).where(ageGroupJoin.get("slug").in(filterRequest.getAgeSlugs()));
                predicates.add(cb.exists(subquery));
            } else if (filterRequest.getAgeGroupId() != null) {
                jakarta.persistence.criteria.Subquery<String> subquery = query.subquery(String.class);
                jakarta.persistence.criteria.Root<Resource> subRoot = subquery.correlate(root);
                jakarta.persistence.criteria.Join<Resource, AgeGroup> ageGroupJoin = subRoot.join("ageGroups");
                subquery.select(subRoot.get("id"))
                        .where(cb.equal(ageGroupJoin.get("id"), filterRequest.getAgeGroupId()));
                predicates.add(cb.exists(subquery));
            }

            // Keyword Filter
            if (filterRequest.getKeyword() != null && !filterRequest.getKeyword().isEmpty()) {
                String likePattern = "%" + filterRequest.getKeyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), likePattern));
            }

            // Creator Filter
            if (filterRequest.getCreatedBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), filterRequest.getCreatedBy()));
            }

            // Resource Type Filter (Multi-select)
            if (filterRequest.getTypes() != null && !filterRequest.getTypes().isEmpty()) {
                predicates.add(root.get("fileType").in(filterRequest.getTypes()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Page<ResourceResponse> executeQueryAndMap(Specification<Resource> spec, Pageable pageable,
            Long currentUserId) {
        Page<Resource> resourcePage = resourceRepository.findAll(spec, pageable);

        List<String> resourceIds = resourcePage.getContent().stream()
                .map(Resource::getId)
                .collect(Collectors.toList());

        List<Long> topicIds = resourcePage.getContent().stream().map(Resource::getTopic)
                .filter(Objects::nonNull).map(Topic::getId).distinct().toList();
        Map<Long, Long> resourceCountsByTopic = topicIds.isEmpty() ? Collections.emptyMap()
                : topicRepository.countActiveResourcesByTopicIds(topicIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        resourcePage.forEach(resource -> {
            if (resource.getTopic() != null) {
                resource.getTopic().setResourceCount(
                        resourceCountsByTopic.getOrDefault(resource.getTopic().getId(), 0L));
            }
        });

        Set<String> favoritedResourceIds = Collections.emptySet();
        if (currentUserId != null && !resourceIds.isEmpty()) {
            favoritedResourceIds = favoriteRepository
                    .findFavoritedResourceIdsByUserIdAndResourceIdIn(currentUserId, resourceIds);
        }

        Map<String, Long> pendingViews = resourceStatService.getPendingViewCounts(resourceIds);
        Map<String, Long> pendingDownloads = resourceStatService.getPendingDownloadCounts(resourceIds);

        final Set<String> finalFavoritedIds = favoritedResourceIds;
        return resourcePage.map(resource -> {
            long pv = pendingViews.getOrDefault(resource.getId(), 0L);
            long pd = pendingDownloads.getOrDefault(resource.getId(), 0L);
            return resourceMapper.toResponse(resource, finalFavoritedIds.contains(resource.getId()), pv, pd);
        });
    }

    private Page<ResourceResponse> executePortalQueryAndMap(Specification<Resource> spec, Pageable pageable,
            Long currentUserId) {
        Page<com.kindergarten.warehouse.repository.projection.ResourceListView> viewPage =
                resourceRepository.findBy(spec, query -> query
                        .as(com.kindergarten.warehouse.repository.projection.ResourceListView.class)
                        .page(pageable));

        List<String> resourceIds = viewPage.getContent().stream()
                .map(com.kindergarten.warehouse.repository.projection.ResourceListView::getId)
                .toList();
        if (resourceIds.isEmpty()) {
            return viewPage.map(view -> resourceMapper.toResponse(view, List.of(), null, false, 0L, 0L));
        }

        Map<String, List<com.kindergarten.warehouse.dto.response.AgeGroupResponse>> ageGroupsByResource =
                new HashMap<>();
        for (Object[] row : ageGroupRepository.findAgeGroupsByResourceIds(resourceIds)) {
            ageGroupsByResource.computeIfAbsent((String) row[0], ignored -> new ArrayList<>())
                    .add(ageGroupMapper.toResponse((AgeGroup) row[1]));
        }

        List<Long> topicIds = viewPage.getContent().stream()
                .map(com.kindergarten.warehouse.repository.projection.ResourceListView::getTopic)
                .filter(Objects::nonNull)
                .map(com.kindergarten.warehouse.repository.projection.ResourceListView.TopicView::getId)
                .distinct()
                .toList();
        Map<Long, Long> resourceCountsByTopic = topicIds.isEmpty() ? Collections.emptyMap()
                : topicRepository.countActiveResourcesByTopicIds(topicIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Set<String> favoriteIds = currentUserId == null ? Collections.emptySet()
                : favoriteRepository.findFavoritedResourceIdsByUserIdAndResourceIdIn(currentUserId, resourceIds);
        Map<String, Long> pendingViews = resourceStatService.getPendingViewCounts(resourceIds);
        Map<String, Long> pendingDownloads = resourceStatService.getPendingDownloadCounts(resourceIds);

        return viewPage.map(view -> resourceMapper.toResponse(
                view,
                ageGroupsByResource.getOrDefault(view.getId(), List.of()),
                view.getTopic() == null ? null
                        : resourceCountsByTopic.getOrDefault(view.getTopic().getId(), 0L),
                favoriteIds.contains(view.getId()),
                pendingViews.getOrDefault(view.getId(), 0L),
                pendingDownloads.getOrDefault(view.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getPortalResources(ResourceFilterRequest filterRequest, int page, int size,
            com.kindergarten.warehouse.security.Viewer viewer) {
        Pageable pageable = PageableUtils.createPageable(page, size, "createdAt", "desc");
        Specification<Resource> baseSpec = createBaseSpecification(filterRequest);
        Specification<Resource> portalSpec =
                com.kindergarten.warehouse.security.ResourceVisibilitySpecifications.portalVisibleTo(viewer);

        Specification<Resource> finalSpec = Specification.where(portalSpec).and(baseSpec);

        return executePortalQueryAndMap(finalSpec, pageable, viewer.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getAdminResources(ResourceFilterRequest filterRequest, int page, int size) {
        Pageable pageable = PageableUtils.createPageable(page, size, "createdAt", "desc");

        Specification<Resource> baseSpec = createBaseSpecification(filterRequest);
        Specification<Resource> adminSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if ("DELETED".equals(filterRequest.getStatus())) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
                if (filterRequest.getStatus() != null && !filterRequest.getStatus().isEmpty()) {
                    ResourceStatus status = parseStatusOrThrow(filterRequest.getStatus());
                    predicates.add(cb.equal(root.get("status"), status));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<Resource> finalSpec = Specification.where(adminSpec).and(baseSpec);

        User currentUser = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        }

        return executeQueryAndMap(finalSpec, pageable,
                currentUser == null ? null : currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getMyResources(ResourceFilterRequest filterRequest, int page, int size,
            String username) {
        Pageable pageable = PageableUtils.createPageable(page, size, "createdAt", "desc");
        User currentUser = getUserOrThrow(username);

        Specification<Resource> baseSpec = createBaseSpecification(filterRequest);
        Specification<Resource> mySpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("createdBy"), currentUser.getId()));

            if (filterRequest.getStatus() != null && !filterRequest.getStatus().isEmpty()) {
                ResourceStatus status = parseStatusOrThrow(filterRequest.getStatus());
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<Resource> finalSpec = Specification.where(mySpec).and(baseSpec);

        return executeQueryAndMap(finalSpec, pageable, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getFavoriteResources(int page, int size,
            com.kindergarten.warehouse.security.Viewer viewer) {
        Pageable pageable = PageableUtils.createPageable(page, size, "createdAt", "desc");
        List<Resource> resources = getVisibleFavoriteResources(viewer);

        int start = Math.toIntExact(Math.min(pageable.getOffset(), resources.size()));
        int end = Math.min(start + pageable.getPageSize(), resources.size());
        List<Resource> pageContent = resources.subList(start, end);

        List<String> resourceIds = pageContent.stream().map(Resource::getId).collect(Collectors.toList());
        Map<String, Long> pendingViews = resourceStatService.getPendingViewCounts(resourceIds);
        Map<String, Long> pendingDownloads = resourceStatService.getPendingDownloadCounts(resourceIds);

        List<ResourceResponse> content = pageContent.stream()
                .map(resource -> resourceMapper.toResponse(
                        resource,
                        true,
                        pendingViews.getOrDefault(resource.getId(), 0L),
                        pendingDownloads.getOrDefault(resource.getId(), 0L)))
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, resources.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getFavoriteResourceIds(com.kindergarten.warehouse.security.Viewer viewer) {
        return getVisibleFavoriteResources(viewer).stream()
                .map(Resource::getId)
                .collect(Collectors.toList());
    }

    @Override
    public void incrementViewCount(String id, String ipAddress) {
        resourceStatService.incrementViewCount(id, ipAddress);
    }

    @Override
    public void incrementDownloadCount(String id) {
        resourceStatService.incrementDownloadCount(id);
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.DELETE, description = "Deleted resource", target = "RESOURCE")
    public void deleteResource(String id, String username, boolean hard) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        validateResourceOwnership(resource, user);

        if (hard) {
            deleteResourceFiles(resource);
            favoriteRepository.deleteByResourceId(id);
            resourceRepository.delete(resource);
        } else {
            resource.setIsDeleted(true);
            resourceRepository.save(resource);
        }
    }

    @Override
    @Transactional
    public void deleteResources(List<String> ids, String username, boolean hard) {
        List<Resource> resources = resourceRepository.findAllById(ids);
        if (resources.isEmpty())
            return;

        User user = getUserOrThrow(username);
        for (Resource resource : resources) {
            validateResourceOwnership(resource, user);
            if (hard) {
                deleteResourceFiles(resource);
            } else {
                resource.setIsDeleted(true);
            }

            String logDetail = String.format("Bulk Deleted document: %s (Hard Delete: %b)", resource.getTitle(), hard);
            manuallyLogAudit("DELETE_BULK", username, "RESOURCE_STATUS", logDetail);
        }

        if (hard) {
            favoriteRepository.deleteByResourceIdIn(ids);
            resourceRepository.deleteAll(resources);
        } else {
            resourceRepository.saveAll(resources);
        }
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.RESTORE, description = "Restored resource", target = "RESOURCE")
    public void restoreResource(String id, String username) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        validateResourceOwnership(resource, user);

        resource.setIsDeleted(false);
        resourceRepository.save(resource);
    }

    @Override
    @Transactional
    public void restoreResources(List<String> ids, String username) {
        List<Resource> resources = resourceRepository.findAllById(ids);
        if (resources.isEmpty())
            return;

        User user = getUserOrThrow(username);
        for (Resource resource : resources) {
            validateResourceOwnership(resource, user);
            resource.setIsDeleted(false);

            String logDetail = String.format("Bulk Restored document: %s", resource.getTitle());
            manuallyLogAudit("RESTORE_BULK", username, "RESOURCE_STATUS", logDetail);
        }
        resourceRepository.saveAll(resources);
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Updated resource", target = "RESOURCE_UPDATE")
    public ResourceResponse updateResource(String id, ResourceUpdateRequest request, String username) {
        Resource resource = resourceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        validateResourceOwnership(resource, user);

        if (request.getFile() != null && !request.getFile().isEmpty()
                && request.getYoutubeLink() != null && !request.getYoutubeLink().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        // Snapshot pre-edit state to compute the diff for the audit log.
        String prevTitle = resource.getTitle();
        String prevDescription = resource.getDescription();
        String prevFileUrl = resource.getFileUrl();
        Long prevTopicId = resource.getTopic() != null ? resource.getTopic().getId() : null;
        List<String> changedFields = new ArrayList<>();

        if (request.getTitle() != null) {
            if (!java.util.Objects.equals(prevTitle, request.getTitle())) changedFields.add("title");
            resource.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            if (!java.util.Objects.equals(prevDescription, request.getDescription())) changedFields.add("description");
            resource.setDescription(request.getDescription());
        }
        if (isAdmin(user)) {
            // ADMIN WORKFLOW: Allow Admin to change status directly from the form.
            // If they don't provide a status, it stays whatever it was originally.
            if (request.getStatus() != null) {
                resource.setStatus(request.getStatus());
                // Clear rejection reason if moving away from REJECTED
                if (request.getStatus() == ResourceStatus.APPROVED || request.getStatus() == ResourceStatus.PENDING) {
                    resource.setRejectionReason(null);
                }
            }
        } else {
            // UPLOADER WORKFLOW (ZERO TRUST):
            // NEVER trust the 'status' field from the Uploader's payload.
            // If an Uploader updates their file, it ALWAYS goes back to PENDING.
            // Even if it was APPROVED or REJECTED before, editing the content demands a new
            // review.
            resource.setStatus(ResourceStatus.PENDING);
            resource.setRejectionReason(null);
        }
        // Ignore client-supplied fileType; backend derives from file/link.

        if (request.getFile() != null && !request.getFile().isEmpty()) {
            // Delete old file if present
            if (resource.getResourceType() == ResourceType.FILE && resource.getFileUrl() != null
                    && !resource.getFileUrl().isEmpty()) {
                try {
                    minioStorageService.deleteFile(resource.getFileUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old file during update: {}", e.getMessage());
                }
            }
            String path = AppConstants.BUCKET_RESOURCES + "/" + AppConstants.FOLDER_FILES;
            String newFileUrl = minioStorageService.uploadFile(request.getFile(), path);
            resource.setFileUrl(newFileUrl);
            resource.setResourceType(ResourceType.FILE);
            String extension = getExtension(request.getFile().getOriginalFilename());
            resource.setFileExtension(extension);
            resource.setFileType(determineFileType(extension).name());
            resource.setFileSize(request.getFile().getSize());
        }

        if (request.getYoutubeLink() != null && !request.getYoutubeLink().isEmpty()) {
            String youtubeId = youtubeService.extractVideoId(request.getYoutubeLink());
            if (youtubeId == null) {
                throw new AppException(ErrorCode.INVALID_YOUTUBE_LINK);
            }

            boolean isAccessible = true;
            try {
                isAccessible = youtubeService.isVideoAccessible(youtubeId);
            } catch (Exception e) {
                log.warn("Cannot verify YouTube video accessibility: {}", e.getMessage());
            }
            if (!isAccessible) {
                throw new AppException(ErrorCode.INVALID_YOUTUBE_LINK);
            }

            resource.setFileUrl(request.getYoutubeLink());
            resource.setResourceType(ResourceType.YOUTUBE);
            resource.setFileType("VIDEO");
            resource.setFileExtension("youtube");

            if (resource.getThumbnailUrl() == null || resource.getThumbnailUrl().isEmpty()
                    || resource.getThumbnailUrl().contains("youtube.com")) {
                resource.setThumbnailUrl("https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg");
            }

            try {
                String duration = youtubeService.getVideoDuration(youtubeId);
                if (duration != null && duration.length() <= 20) {
                    resource.setDuration(duration);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch YouTube duration for video {}: {}", youtubeId, e.getMessage());
                // Duration optional - continue with update
            }
        }

        if (request.getThumbnail() != null && !request.getThumbnail().isEmpty()) {
            String thumbnailContentType = request.getThumbnail().getContentType();
            if (!isValidImageFormat(thumbnailContentType)) {
                throw new AppException(ErrorCode.INVALID_IMAGE_FORMAT);
            }

            if (request.getThumbnail().getSize() > thumbnailMaxBytes) {
                throw new AppException(ErrorCode.THUMBNAIL_TOO_LARGE);
            }

            if (resource.getThumbnailUrl() != null && !resource.getThumbnailUrl().isEmpty()
                    && !resource.getThumbnailUrl().contains("youtube.com")) {
                try {
                    minioStorageService.deleteFile(resource.getThumbnailUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old thumbnail: {}", e.getMessage());
                }
            }

            String path = AppConstants.BUCKET_RESOURCES + "/" + AppConstants.FOLDER_THUMBNAILS;
            String newThumbnailUrl = minioStorageService.uploadFile(request.getThumbnail(), path);
            resource.setThumbnailUrl(newThumbnailUrl);
        }

        if (request.getTopicId() != null) {
            Topic topic = getActiveTopicOrThrow(request.getTopicId());
            if (!java.util.Objects.equals(prevTopicId, topic.getId())) changedFields.add("topic");
            resource.setTopic(topic);
        }

        if (request.getAgeGroupIds() != null && !request.getAgeGroupIds().isEmpty()) {
            resource.setAgeGroups(getAgeGroupsOrThrow(request.getAgeGroupIds()));
            changedFields.add("ageGroups");
        }

        if (!java.util.Objects.equals(prevFileUrl, resource.getFileUrl())) {
            changedFields.add("file");
        }

        Resource savedResource = resourceRepository.save(resource);

        if (!isAdmin(user) && !changedFields.isEmpty()) {
            manuallyLogAudit("RESUBMIT_PENDING", username, "RESOURCE_STATUS",
                    String.format("Uploader edited – fields changed: %s – auto-resubmit for review",
                            changedFields));
        }

        boolean isFavorited = isFavoritedByCurrentUser(savedResource.getId());

        return resourceMapper.toResponse(savedResource, isFavorited);
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Updated resource visibility", target = "RESOURCE_VISIBILITY")
    public ResourceResponse updateVisibility(String id,
            com.kindergarten.warehouse.dto.request.VisibilityUpdateRequest request, String username) {
        Resource resource = resourceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        validateResourceOwnership(resource, user);

        resource.setVisibility(request.getVisibility());
        Resource savedResource = resourceRepository.save(resource);

        boolean isFavorited = isFavoritedByCurrentUser(savedResource.getId());

        return resourceMapper.toResponse(savedResource, isFavorited);
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.UPLOAD, description = "Updated thumbnail", target = "RESOURCE_THUMBNAIL")
    public String updateThumbnail(String id, MultipartFile thumbnail, String username) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        validateResourceOwnership(resource, user);

        if (thumbnail == null || thumbnail.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        String contentType = thumbnail.getContentType();
        if (!isValidImageFormat(contentType)) {
            throw new AppException(ErrorCode.INVALID_IMAGE_FORMAT);
        }

        if (thumbnail.getSize() > thumbnailMaxBytes) {
            throw new AppException(ErrorCode.THUMBNAIL_TOO_LARGE);
        }

        if (resource.getThumbnailUrl() != null && !resource.getThumbnailUrl().isEmpty()
                && !resource.getThumbnailUrl().contains("youtube.com")) {
            try {
                minioStorageService.deleteFile(resource.getThumbnailUrl());
            } catch (Exception e) {
                log.warn("Failed to delete old thumbnail: {}", e.getMessage());
            }
        }

        String path = AppConstants.BUCKET_RESOURCES + "/" + AppConstants.FOLDER_THUMBNAILS;
        String newThumbnailUrl = minioStorageService.uploadFile(thumbnail, path);
        resource.setThumbnailUrl(newThumbnailUrl);
        resourceRepository.save(resource);

        return newThumbnailUrl;
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Toggled favorite", target = "RESOURCE_FAVORITE")
    public boolean toggleFavorite(String resourceId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Resource resource = resourceRepository.findByIdWithDetails(resourceId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        ensurePortalVisible(resource);

        Optional<Favorite> favorite = favoriteRepository.findByUserIdAndResourceId(user.getId(), resourceId);

        if (favorite.isPresent()) {
            favoriteRepository.delete(favorite.get());
            return false; // Was favorited, now removed
        } else {
            Favorite newFavorite = Favorite.builder()
                    .userId(user.getId())
                    .resourceId(resourceId)
                    .build();
            favoriteRepository.save(newFavorite);
            return true; // Now favorited
        }
    }

    private List<Resource> getVisibleFavoriteResources(com.kindergarten.warehouse.security.Viewer viewer) {
        if (!viewer.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        List<String> favoriteIds = favoriteRepository.findResourceIdsByUserId(viewer.userId());
        if (favoriteIds.isEmpty()) {
            return Collections.emptyList();
        }

        Specification<Resource> favoriteIdsSpec = (root, query, cb) -> root.get("id").in(favoriteIds);
        Specification<Resource> visibleFavoriteSpec = Specification
                .where(com.kindergarten.warehouse.security.ResourceVisibilitySpecifications.portalVisibleTo(viewer))
                .and(favoriteIdsSpec);

        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < favoriteIds.size(); i++) {
            order.put(favoriteIds.get(i), i);
        }

        List<Resource> resources = new ArrayList<>(
                resourceRepository.findAll(visibleFavoriteSpec, Pageable.unpaged()).getContent());
        resources.sort(Comparator.comparingInt(resource -> order.getOrDefault(resource.getId(), Integer.MAX_VALUE)));
        return resources;
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceResponse getResourceBySlug(String slug,
            com.kindergarten.warehouse.security.Viewer viewer) {
        // findBySlug CỐ Ý không lọc isDeleted/ARCHIVED — lọc ở tầng truy vấn thì
        // không phân biệt được "đã gỡ" (410) với "không tồn tại" (404).
        // Toàn bộ thứ tự kiểm tra nằm trong ResourceAccessGuard.
        Resource resource = resourceAccessGuard.requireViewable(
                resourceRepository.findBySlug(slug).orElse(null), viewer);

        boolean isFavorited = false;
        if (viewer.isAuthenticated()) {
            try {
                isFavorited = favoriteRepository.existsByUserIdAndResourceId(
                        viewer.userId(), resource.getId());
            } catch (Exception e) {
                log.warn("Failed to check favorite status for resource {}: {}", slug, e.getMessage());
            }
        }

        return resourceMapper.toResponse(resource, isFavorited);
    }

    private User getUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Topic getActiveTopicOrThrow(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
        Category category = topic.getCategory();
        if (Boolean.TRUE.equals(topic.getIsDeleted())
                || topic.getVisibility() != Visibility.PUBLIC
                || category == null
                || Boolean.TRUE.equals(category.getIsDeleted())
                || category.getVisibility() != Visibility.PUBLIC) {
            throw new AppException(ErrorCode.TOPIC_NOT_FOUND);
        }
        return topic;
    }

    private Set<AgeGroup> getAgeGroupsOrThrow(List<Long> ageGroupIds) {
        if (ageGroupIds == null || ageGroupIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> uniqueIds = new HashSet<>(ageGroupIds);
        List<AgeGroup> ageGroups = ageGroupRepository.findAllById(uniqueIds);
        if (ageGroups.size() != uniqueIds.size()) {
            throw new AppException(ErrorCode.AGE_GROUP_NOT_FOUND);
        }
        return new HashSet<>(ageGroups);
    }

    private void ensurePortalVisible(Resource resource) {
        if (resource.getVisibility() != Visibility.PUBLIC
                || resource.getStatus() != ResourceStatus.APPROVED
                || Boolean.TRUE.equals(resource.getIsDeleted())
                || resource.getTopic() == null
                || Boolean.TRUE.equals(resource.getTopic().getIsDeleted())
                || resource.getTopic().getVisibility() != Visibility.PUBLIC
                || resource.getTopic().getCategory() == null
                || Boolean.TRUE.equals(resource.getTopic().getCategory().getIsDeleted())
                || resource.getTopic().getCategory().getVisibility() != Visibility.PUBLIC) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private boolean isFavoritedByCurrentUser(String resourceId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return false;
            }
            return userRepository.findByUsername(authentication.getName())
                    .map(user -> favoriteRepository.existsByUserIdAndResourceId(user.getId(), resourceId))
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Failed to check favorite status for resource {}: {}", resourceId, e.getMessage());
            return false;
        }
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> role == Role.ADMIN);
    }

    // isPrivileged(User) da bi XOA.
    //
    // No tra true cho MOI TEACHER, khong chi chu so huu, nen getResourceBySlug
    // cu cho phep giao vien bat ky xem tai lieu PRIVATE cua giao vien khac va
    // bo qua luon kiem tra status. Trai BUSINESS_RULES_V1 §3.3.
    //
    // Thay bang ResourceAccessGuard + VisibilityPolicy, noi phan biet ro
    // "co vai tro" voi "la chu so huu". Dung khoi phuc method nay.

    private ResourceStatus parseStatusOrThrow(String statusValue) {
        try {
            return ResourceStatus.valueOf(statusValue);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateResourceOwnership(Resource resource, User user) {
        if (isAdmin(user))
            return;
        boolean isAdmin = isAdmin(user);
        if (!isAdmin && !resource.getCreatedBy().equals(user.getId())) {
            throw new AppException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private void deleteResourceFiles(Resource resource) {
        if (resource.getThumbnailUrl() != null && !resource.getThumbnailUrl().isEmpty()
                && !resource.getThumbnailUrl().contains("youtube.com")) {
            try {
                minioStorageService.deleteFile(resource.getThumbnailUrl());
            } catch (Exception e) {
                log.warn("Failed to delete thumbnail: {}", e.getMessage());
            }
        }
        if (resource.getResourceType() == ResourceType.FILE && resource.getFileUrl() != null
                && !resource.getFileUrl().isEmpty()) {
            try {
                minioStorageService.deleteFile(resource.getFileUrl());
            } catch (Exception e) {
                log.warn("Failed to delete file: {}", e.getMessage());
            }
        }
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
                throw new AppException(ErrorCode.FILE_TYPE_INVALID);
        }
    }

    private boolean isValidImageFormat(String contentType) {
        if (contentType == null)
            return false;
        String lowerCaseType = contentType.toLowerCase();
        return lowerCaseType.equals("image/jpeg") ||
                lowerCaseType.equals("image/jpg") ||
                lowerCaseType.equals("image/png") ||
                lowerCaseType.equals("image/webp");
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadInfo getResourceFileInfo(String id,
            com.kindergarten.warehouse.security.Viewer viewer) throws Exception {
        // Dùng fetch-join để topic/category được load sẵn: method không chạy trong
        // transaction + OSIV tắt nên truy cập lazy proxy sẽ ném LazyInitializationException.
        Resource resource = resourceAccessGuard.requireDownloadable(
                resourceRepository.findByIdWithDetails(id).orElse(null), viewer);

        // Only objects stored by this service have a downloadable stream.
        if (resource.getResourceType() != ResourceType.FILE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        resourceStatService.incrementDownloadCount(resource.getId());

        String extension = normalizeExtension(resource.getFileExtension());
        String fileName = buildDownloadFileName(resource, extension);
        String contentType = getContentTypeByExtension(extension);
        var inputStream = minioStorageService.getObject(resource.getFileUrl());

        return FileDownloadInfo.builder()
                .inputStream(inputStream)
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(resource.getFileSize() != null ? resource.getFileSize() : 0L)
                .build();
    }

    private String getContentTypeByExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "application/octet-stream";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            default -> "application/octet-stream";
        };
    }

    private String buildDownloadFileName(Resource resource, String extension) {
        String title = resource.getTitle() == null ? "" : resource.getTitle();
        String safeBaseName = title.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        if (!StringUtils.hasText(safeBaseName)) {
            safeBaseName = "resource-" + resource.getId();
        }
        if (!StringUtils.hasText(extension)) {
            return safeBaseName;
        }
        return safeBaseName + "." + extension;
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        String normalized = extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        return normalized.matches("[a-z0-9]{1,10}") ? normalized : "";
    }

    @Override
    @Transactional
    public ResourceResponse approveResource(String id, String username) {
        Resource resource = resourceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        if (!isAdmin(user)) {
            throw new AppException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        resource.setStatus(ResourceStatus.APPROVED);
        resource.setRejectionReason(null);
        Resource savedResource = resourceRepository.save(resource);

        String detail = String.format("Approved document: %s", resource.getTitle());
        manuallyLogAudit("APPROVE", username, "RESOURCE_STATUS", detail);

        return resourceMapper.toResponse(savedResource, false);
    }

    @Override
    @Transactional
    public ResourceResponse rejectResource(String id, String reason, String username) {
        Resource resource = resourceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User user = getUserOrThrow(username);
        if (!isAdmin(user)) {
            throw new AppException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        resource.setStatus(ResourceStatus.REJECTED);
        resource.setRejectionReason(reason);
        Resource savedResource = resourceRepository.save(resource);

        String detail = String.format("Rejected document: %s | Reason: %s", resource.getTitle(), reason);
        manuallyLogAudit("REJECT", username, "RESOURCE_STATUS", detail);

        try {
            if (resource.getCreatedBy() != null) {
                userRepository.findById(resource.getCreatedBy()).ifPresent(uploader -> {
                    ResourceRejectedEvent event = ResourceRejectedEvent.builder()
                            .uploaderId(String.valueOf(uploader.getId()))
                            .uploaderEmail(uploader.getEmail())
                            .uploaderName(
                                    uploader.getFullName() != null ? uploader.getFullName() : uploader.getUsername())
                            .documentTitle(resource.getTitle())
                            .reason(reason)
                            .build();

                    log.info("Publishing ResourceRejectedEvent for resource ID: {}", id);
                    eventPublisher.publishEvent(event);
                });
            }
        } catch (Exception e) {
            log.error("Failed to lookup original uploader or publish rejection event.", e);
        }

        return resourceMapper.toResponse(savedResource, false);
    }

    private void manuallyLogAudit(String action, String username, String target, String detail) {
        try {
            String ipAddress = "UNKNOWN";
            String userAgent = "UNKNOWN";
            try {
                ipAddress = com.kindergarten.warehouse.util.RequestUtils.getClientIpAddress();
                userAgent = com.kindergarten.warehouse.util.RequestUtils.getUserAgent();
            } catch (Exception ignored) {
            }
            auditLogService.saveLog(action, username, target, detail, ipAddress, userAgent);
        } catch (Exception e) {
            log.error("Failed to manually save audit log", e);
        }
    }

    // -----------------------------------------------------------------------------------------
    // BULK OPERATIONS
    // -----------------------------------------------------------------------------------------

    @Override
    @Transactional
    public BulkOperationResponse bulkApprove(BulkResourceRequest request, String username) {
        User user = getUserOrThrow(username);
        if (!isAdmin(user)) {
            throw new AppException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        List<String> requestedIds = request.getResourceIds();
        List<Resource> loaded = resourceRepository.findAllById(requestedIds);
        Map<String, Resource> byId = loaded.stream()
                .collect(Collectors.toMap(Resource::getId, java.util.function.Function.identity()));

        List<Resource> toSave = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        List<String> auditDetails = new ArrayList<>();
        int successCount = 0;

        for (String id : requestedIds) {
            Resource resource = byId.get(id);
            if (resource == null) {
                failedIds.add(id);
                continue;
            }
            if (resource.getStatus() != ResourceStatus.APPROVED) {
                resource.setStatus(ResourceStatus.APPROVED);
                resource.setRejectionReason(null);
                toSave.add(resource);
                auditDetails.add(String.format("Bulk Approved document: %s", resource.getTitle()));
            }
            successCount++;
        }

        if (!toSave.isEmpty()) {
            resourceRepository.saveAll(toSave);
            auditDetails.forEach(d -> manuallyLogAudit("APPROVE_BULK", username, "RESOURCE_STATUS", d));
        }

        return BulkOperationResponse.builder()
                .successCount(successCount)
                .failedIds(failedIds)
                .message("Bulk approve operation completed.")
                .build();
    }

    @Override
    @Transactional
    public BulkOperationResponse bulkReject(BulkResourceRequest request, String username) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        User user = getUserOrThrow(username);
        if (!isAdmin(user)) {
            throw new AppException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        List<String> requestedIds = request.getResourceIds();
        List<Resource> loaded = resourceRepository.findAllById(requestedIds);
        Map<String, Resource> byId = loaded.stream()
                .collect(Collectors.toMap(Resource::getId, java.util.function.Function.identity()));

        List<Long> uploaderIds = loaded.stream()
                .map(Resource::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> uploadersById = uploaderIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(uploaderIds).stream()
                        .collect(Collectors.toMap(User::getId, java.util.function.Function.identity()));

        List<Resource> toSave = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        List<String> auditDetails = new ArrayList<>();
        List<ResourceRejectedEvent> pendingEvents = new ArrayList<>();
        int successCount = 0;

        for (String id : requestedIds) {
            Resource resource = byId.get(id);
            if (resource == null) {
                failedIds.add(id);
                continue;
            }

            if (resource.getStatus() != ResourceStatus.REJECTED) {
                resource.setStatus(ResourceStatus.REJECTED);
                resource.setRejectionReason(request.getReason());
                toSave.add(resource);

                auditDetails.add(String.format("Bulk Rejected document: %s | Reason: %s",
                        resource.getTitle(), request.getReason()));

                User uploader = uploadersById.get(resource.getCreatedBy());
                if (uploader != null) {
                    pendingEvents.add(ResourceRejectedEvent.builder()
                            .uploaderId(String.valueOf(uploader.getId()))
                            .uploaderEmail(uploader.getEmail())
                            .uploaderName(uploader.getFullName() != null ? uploader.getFullName() : uploader.getUsername())
                            .documentTitle(resource.getTitle())
                            .reason(request.getReason())
                            .build());
                }
            }
            successCount++;
        }

        if (!toSave.isEmpty()) {
            resourceRepository.saveAll(toSave);
            // Audit + event publish only after a successful save so we don't log work
            // that ended up rolled back.
            auditDetails.forEach(d -> manuallyLogAudit("REJECT_BULK", username, "RESOURCE_STATUS", d));
            for (ResourceRejectedEvent event : pendingEvents) {
                try {
                    eventPublisher.publishEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to publish rejection event during bulk operation: {}", e.getMessage());
                }
            }
        }

        return BulkOperationResponse.builder()
                .successCount(successCount)
                .failedIds(failedIds)
                .message("Bulk reject operation completed.")
                .build();
    }
}
