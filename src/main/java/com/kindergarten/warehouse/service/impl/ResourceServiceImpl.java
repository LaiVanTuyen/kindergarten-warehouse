package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.FileType;
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

import java.util.UUID;

@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;

    public ResourceServiceImpl(ResourceRepository resourceRepository, TopicRepository topicRepository,
            UserRepository userRepository, MinioStorageService minioStorageService) {
        this.resourceRepository = resourceRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.minioStorageService = minioStorageService;
    }

    @Override
    public ResourceResponse uploadResource(MultipartFile file, String title,
            String description, Long topicId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.TOPIC_NOT_FOUND));

        String fileUrl = minioStorageService.uploadFile(file);
        String extension = getExtension(file.getOriginalFilename());
        FileType type = determineFileType(extension);

        Resource resource = new Resource();
        resource.setTitle(title);
        resource.setDescription(description);
        resource.setTopic(topic);
        resource.setFileUrl(fileUrl);
        resource.setFileExtension(extension);
        resource.setFileType(type);
        resource.setFileSize(file.getSize());
        resource.setCreatedBy(user);

        return mapToResponse(resourceRepository.save(resource));
    }

    @Override
    public Page<ResourceResponse> getResources(Long topicId, Long categoryId, int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Resource> resourcePage;
        if (topicId != null) {
            resourcePage = resourceRepository.findByTopicId(topicId, pageable);
        } else if (categoryId != null) {
            resourcePage = resourceRepository.findByTopicCategoryId(categoryId, pageable);
        } else {
            resourcePage = resourceRepository.findAll(pageable);
        }
        return resourcePage.map(this::mapToResponse);
    }

    private ResourceResponse mapToResponse(Resource resource) {
        return ResourceResponse.builder()
                .id(resource.getId())
                .title(resource.getTitle())
                .description(resource.getDescription())
                .viewsCount(resource.getViewsCount())
                .createdAt(resource.getCreatedAt())
                .fileUrl(resource.getFileUrl())
                .fileType(resource.getFileType())
                .fileExtension(resource.getFileExtension())
                .topicId(resource.getTopic().getId())
                .topicName(resource.getTopic().getName())
                .fileSize(resource.getFileSize())
                .createdBy(resource.getCreatedBy() != null ? resource.getCreatedBy().getFullName() : null)
                .build();
    }

    @Override
    public void incrementViewCount(UUID id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND));
        resource.setViewsCount(resource.getViewsCount() + 1);
        resourceRepository.save(resource);
    }

    @Override
    public void deleteResource(UUID id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.RESOURCE_NOT_FOUND));

        // Delete file from MinIO
        minioStorageService.deleteFile(resource.getFileUrl());

        resourceRepository.deleteById(id);
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
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
}
