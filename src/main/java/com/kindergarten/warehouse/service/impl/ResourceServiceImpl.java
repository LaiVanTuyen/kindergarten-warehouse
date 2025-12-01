package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceType;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.service.FirebaseService;
import com.kindergarten.warehouse.service.ResourceService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final TopicRepository topicRepository;
    private final FirebaseService firebaseService;
    private final MessageSource messageSource;

    public ResourceServiceImpl(ResourceRepository resourceRepository, TopicRepository topicRepository,
            FirebaseService firebaseService, MessageSource messageSource) {
        this.resourceRepository = resourceRepository;
        this.topicRepository = topicRepository;
        this.firebaseService = firebaseService;
        this.messageSource = messageSource;
    }

    @Override
    public Resource uploadResource(MultipartFile file, String title, String description, Long topicId) {
        try {
            Topic topic = topicRepository.findById(topicId)
                    .orElseThrow(() -> new RuntimeException(
                            messageSource.getMessage("error.topic.not_found", null, LocaleContextHolder.getLocale())));

            String fileUrl = firebaseService.uploadFile(file);
            String extension = getExtension(file.getOriginalFilename());
            ResourceType type = determineResourceType(extension);

            Resource resource = new Resource();
            resource.setTitle(title);
            resource.setDescription(description);
            resource.setTopic(topic);
            resource.setFileUrl(fileUrl);
            resource.setFileExtension(extension);
            resource.setFileType(type);

            return resourceRepository.save(resource);

        } catch (IOException e) {
            throw new RuntimeException(
                    messageSource.getMessage("error.firebase.init", null, LocaleContextHolder.getLocale()), e);
        }
    }

    @Override
    public Page<Resource> getResources(Long topicId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (topicId != null) {
            return resourceRepository.findByTopicId(topicId, pageable);
        } else {
            return resourceRepository.findAll(pageable);
        }
    }

    @Override
    public void deleteResource(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        messageSource.getMessage("error.resource.not_found", null, LocaleContextHolder.getLocale())));

        // Delete file from Firebase
        firebaseService.deleteFile(resource.getFileUrl());

        resourceRepository.deleteById(id);
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private ResourceType determineResourceType(String extension) {
        switch (extension) {
            case "mp4":
            case "mov":
            case "avi":
                return ResourceType.VIDEO;
            case "doc":
            case "docx":
                return ResourceType.DOCUMENT;
            case "xls":
            case "xlsx":
                return ResourceType.EXCEL;
            case "pdf":
                return ResourceType.PDF;
            default:
                return ResourceType.DOCUMENT; // Default fallback
        }
    }
}
