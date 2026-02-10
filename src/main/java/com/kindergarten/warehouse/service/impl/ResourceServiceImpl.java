package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.*;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.*;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.ResourceService;
import com.kindergarten.warehouse.service.ResourceStatService;
import com.kindergarten.warehouse.util.AppConstants;
import com.kindergarten.warehouse.util.SlugUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final CacheManager cacheManager;
    private final ResourceMapper resourceMapper;
    private final ResourceStatService resourceStatService;

    @Override
    @Transactional
    @LogAction(action = AuditAction.CREATE, description = "Uploaded resource", target = "RESOURCE")
    public ResourceResponse uploadResource(ResourceCreationRequest request, String username) {
        if ((request.getFile() == null || request.getFile().isEmpty()) && 
            (request.getYoutubeLink() == null || request.getYoutubeLink().isEmpty())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Topic topic = topicRepository.findById(request.getTopicId())
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));

        Set<AgeGroup> ageGroups = new HashSet<>();
        if (request.getAgeGroupIds() != null && !request.getAgeGroupIds().isEmpty()) {
            ageGroups.addAll(ageGroupRepository.findAllById(request.getAgeGroupIds()));
        }

        String fileUrl;
        ResourceType resourceType;
        String fileType;
        String extension = "";
        Long fileSize = 0L;

        if (request.getYoutubeLink() != null && !request.getYoutubeLink().isEmpty()) {
            String youtubeId = extractYoutubeId(request.getYoutubeLink());
            if (youtubeId == null) {
                throw new IllegalArgumentException("Invalid YouTube Link");
            }
            fileUrl = request.getYoutubeLink();
            resourceType = ResourceType.YOUTUBE;
            fileType = "VIDEO";
            extension = "youtube";
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
            String path = AppConstants.BUCKET_RESOURCES + "/" + AppConstants.FOLDER_THUMBNAILS;
            thumbnailUrl = minioStorageService.uploadFile(request.getThumbnail(), path);
        } else if (resourceType == ResourceType.YOUTUBE) {
            String youtubeId = extractYoutubeId(fileUrl);
            thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg";
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
        resource.setCreatedBy(user.getId());
        resource.setCreator(user);
        resource.setAgeGroups(ageGroups);

        boolean isAdmin = user.getRoles().stream().anyMatch(role -> role == Role.ADMIN);
        if (isAdmin) {
            resource.setStatus(ResourceStatus.APPROVED);
        } else {
            resource.setStatus(ResourceStatus.PENDING);
        }

        return resourceMapper.toResponse(resourceRepository.save(resource), false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getResources(
            ResourceFilterRequest filterRequest, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<Resource> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if ("DELETED".equals(filterRequest.getStatus())) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
                if (filterRequest.getStatus() != null && !filterRequest.getStatus().isEmpty()) {
                    predicates.add(cb.equal(root.get("status"), ResourceStatus.valueOf(filterRequest.getStatus())));
                }
            }

            if (filterRequest.getTopicSlug() != null && !filterRequest.getTopicSlug().isEmpty()) {
                predicates.add(cb.equal(root.get("topic").get("slug"), filterRequest.getTopicSlug()));
            } else if (filterRequest.getCategorySlug() != null && !filterRequest.getCategorySlug().isEmpty()) {
                predicates
                        .add(cb.equal(root.get("topic").get("category").get("slug"), filterRequest.getCategorySlug()));
            }

            if (filterRequest.getTopicId() != null) {
                predicates.add(cb.equal(root.get("topic").get("id"), filterRequest.getTopicId()));
            } else if (filterRequest.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("topic").get("category").get("id"), filterRequest.getCategoryId()));
            }

            if (filterRequest.getAgeGroupId() != null) {
                predicates.add(cb.equal(root.join("ageGroups").get("id"), filterRequest.getAgeGroupId()));
            }

            if (filterRequest.getAgeSlugs() != null && !filterRequest.getAgeSlugs().isEmpty()) {
                predicates.add(root.join("ageGroups").get("slug").in(filterRequest.getAgeSlugs()));
            }

            if (filterRequest.getKeyword() != null && !filterRequest.getKeyword().isEmpty()) {
                String likePattern = "%" + filterRequest.getKeyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), likePattern));
            }
            
            if (filterRequest.getCreatedBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), filterRequest.getCreatedBy()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Resource> resourcePage = resourceRepository.findAll(spec, pageable);
        
        Set<String> favoritedResourceIds = Collections.emptySet();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();
            User currentUser = userRepository.findByUsername(username).orElse(null);
            if (currentUser != null) {
                List<String> resourceIds = resourcePage.getContent().stream()
                        .map(Resource::getId)
                        .collect(Collectors.toList());
                if (!resourceIds.isEmpty()) {
                    favoritedResourceIds = favoriteRepository.findFavoritedResourceIdsByUserIdAndResourceIdIn(currentUser.getId(), resourceIds);
                }
            }
        }

        final Set<String> finalFavoritedIds = favoritedResourceIds;
        return resourcePage.map(resource -> resourceMapper.toResponse(resource, finalFavoritedIds.contains(resource.getId())));
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
    public void deleteResource(String id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        resource.setIsDeleted(true);
        resourceRepository.save(resource);

        Cache cache = cacheManager.getCache("resources");
        if (cache != null) {
            cache.evict(resource.getSlug());
        }
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.RESTORE, description = "Restored resource", target = "RESOURCE")
    public void restoreResource(String id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        resource.setIsDeleted(false);
        resourceRepository.save(resource);

        Cache cache = cacheManager.getCache("resources");
        if (cache != null) {
            cache.evict(resource.getSlug());
        }
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Updated resource", target = "RESOURCE_UPDATE")
    public ResourceResponse updateResource(String id, ResourceUpdateRequest request) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getTitle() != null) resource.setTitle(request.getTitle());
        if (request.getDescription() != null) resource.setDescription(request.getDescription());
        if (request.getStatus() != null) resource.setStatus(request.getStatus());
        if (request.getFileType() != null) resource.setFileType(request.getFileType());
        
        if (request.getYoutubeLink() != null && !request.getYoutubeLink().isEmpty()) {
             String youtubeId = extractYoutubeId(request.getYoutubeLink());
             if (youtubeId == null) {
                 throw new IllegalArgumentException("Invalid YouTube Link");
             }
             resource.setFileUrl(request.getYoutubeLink());
             resource.setResourceType(ResourceType.YOUTUBE);
             resource.setFileType("VIDEO");
             resource.setFileExtension("youtube");
             
             if (resource.getThumbnailUrl() == null || resource.getThumbnailUrl().isEmpty()) {
                 resource.setThumbnailUrl("https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg");
             }
        }

        if (request.getTopicId() != null) {
            Topic topic = topicRepository.findById(request.getTopicId())
                    .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
            resource.setTopic(topic);
        }

        if (request.getAgeGroupIds() != null && !request.getAgeGroupIds().isEmpty()) {
            Set<AgeGroup> ageGroups = new HashSet<>(ageGroupRepository.findAllById(request.getAgeGroupIds()));
            resource.setAgeGroups(ageGroups);
        }

        Resource savedResource = resourceRepository.save(resource);
        return resourceMapper.toResponse(savedResource, false);
    }

    @Override
    @Transactional
    @LogAction(action = AuditAction.UPLOAD, description = "Updated thumbnail", target = "RESOURCE_THUMBNAIL")
    public String updateThumbnail(String id, MultipartFile thumbnail) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (thumbnail == null || thumbnail.isEmpty()) {
            throw new IllegalArgumentException("Thumbnail file is required");
        }

        if (resource.getThumbnailUrl() != null && !resource.getThumbnailUrl().isEmpty() && !resource.getThumbnailUrl().contains("youtube.com")) {
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
    public void toggleFavorite(String resourceId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Optional<Favorite> favorite = favoriteRepository.findByUserIdAndResourceId(user.getId(), resourceId);

        if (favorite.isPresent()) {
            favoriteRepository.delete(favorite.get());
        } else {
            Favorite newFavorite = Favorite.builder()
                    .userId(user.getId())
                    .resourceId(resourceId)
                    .build();
            favoriteRepository.save(newFavorite);
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    @Override
    @Cacheable(value = "resources", key = "#slug")
    public ResourceResponse getResourceBySlug(String slug) {
        Resource resource = resourceRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (resource.getIsDeleted()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        boolean isFavorited = false;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                User currentUser = userRepository.findByUsername(username).orElse(null);
                if (currentUser != null) {
                    isFavorited = favoriteRepository.existsByUserIdAndResourceId(currentUser.getId(), resource.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check favorite status for resource {}: {}", slug, e.getMessage());
        }

        return resourceMapper.toResponse(resource, isFavorited);
    }

    private FileType determineFileType(String extension) {
        switch (extension) {
            case "mp4": case "mov": case "avi": return FileType.VIDEO;
            case "doc": case "docx": return FileType.DOCUMENT;
            case "xls": case "xlsx": return FileType.EXCEL;
            case "pdf": return FileType.PDF;
            default: return FileType.DOCUMENT;
        }
    }
    
    private String extractYoutubeId(String url) {
        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
