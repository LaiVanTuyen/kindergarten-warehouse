package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.AgeGroup;
import com.kindergarten.warehouse.entity.FileType;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import com.kindergarten.warehouse.repository.CommentRepository;
import com.kindergarten.warehouse.repository.FavoriteRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.ResourceService;
import com.kindergarten.warehouse.util.SlugUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    // Map to store IP_ResourceID -> LastViewTimestamp
    private final Map<String, Long> viewTracker = new ConcurrentHashMap<>();

    private final ResourceRepository resourceRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final MinioStorageService minioStorageService;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ResourceMapper resourceMapper;

    @Override
    @LogAction(action = "CREATE", description = "Uploaded resource")
    public ResourceResponse uploadResource(MultipartFile file, String title,
            String description, Long topicId, List<Long> ageGroupIds, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));

        Set<AgeGroup> ageGroups = new HashSet<>();
        if (ageGroupIds != null && !ageGroupIds.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Long> safeIds = (List<Long>) (List<?>) ageGroupIds;
            ageGroups.addAll(ageGroupRepository.findAllById(safeIds));
        }

        String fileUrl = minioStorageService.uploadFile(file, "resources");
        String extension = getExtension(file.getOriginalFilename());
        FileType type = determineFileType(extension);

        Resource resource = new Resource();
        resource.setTitle(title);
        resource.setSlug(SlugUtil.toSlug(title + "-" + System.currentTimeMillis()));
        resource.setDescription(description);
        resource.setTopic(topic);
        resource.setFileUrl(fileUrl);
        resource.setFileExtension(extension);
        resource.setFileType(type.name());
        resource.setFileSize(file.getSize());
        resource.setCreatedBy(user.getId());
        resource.setCreator(user);
        resource.setAgeGroups(ageGroups);

        return resourceMapper.toResponse(resourceRepository.save(resource), false);
    }

    @Override
    public Page<ResourceResponse> getResources(
            ResourceFilterRequest filterRequest, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<Resource> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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

            if (filterRequest.getStatus() != null && !filterRequest.getStatus().isEmpty()) {
                predicates.add(
                        cb.equal(root.get("status"),
                                ResourceStatus.valueOf(filterRequest.getStatus())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Resource> resourcePage = resourceRepository.findAll(spec, pageable);
        return resourcePage.map(this::mapToResponse);
    }

    private ResourceResponse mapToResponse(Resource resource) {
        boolean isFavorited = false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            String username = authentication.getName();
            User currentUser = userRepository.findByUsername(username).orElse(null);
            if (currentUser != null) {
                isFavorited = favoriteRepository.existsByUserIdAndResourceId(currentUser.getId(), resource.getId());
            }
        }
        return resourceMapper.toResponse(resource, isFavorited);
    }

    @Override
    public void incrementViewCount(String id, String ipAddress) {
        String key = ipAddress + "_" + id;
        long currentTime = System.currentTimeMillis();
        long oneHourInMillis = 3600000;

        if (viewTracker.containsKey(key)) {
            long lastViewTime = viewTracker.get(key);
            if (currentTime - lastViewTime < oneHourInMillis) {
                return;
            }
        }

        if (!resourceRepository.existsById(id)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        redisTemplate.opsForValue().increment("kindergarten:views:" + id);
        redisTemplate.opsForSet().add("kindergarten:dirty_views", id);

        viewTracker.put(key, currentTime);
    }

    @Override
    public void incrementDownloadCount(String id) {
        if (!resourceRepository.existsById(id)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        redisTemplate.opsForValue().increment("kindergarten:downloads:" + id);
        redisTemplate.opsForSet().add("kindergarten:dirty_downloads", id);
    }

    @Override
    @LogAction(action = "DELETE", description = "Deleted resource")
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
                return FileType.DOCUMENT;
        }
    }
}
