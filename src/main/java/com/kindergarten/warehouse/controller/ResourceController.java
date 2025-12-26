package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.ResourceService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

import com.kindergarten.warehouse.dto.response.ResourceResponse;

import com.kindergarten.warehouse.dto.response.ApiResponse;

import com.kindergarten.warehouse.service.MessageService;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;
    private final MessageService messageService;

    public ResourceController(ResourceService resourceService, MessageService messageService) {
        this.resourceService = resourceService;
        this.messageService = messageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<ResourceResponse>> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("topicId") Long topicId,
            @RequestParam(value = "ageGroupIds", required = false) java.util.List<Long> ageGroupIds,
            Principal principal) {

        return new ResponseEntity<>(
                ApiResponse.success(
                        resourceService.uploadResource(file, title, description, topicId, ageGroupIds,
                                principal.getName()),
                        messageService.getMessage("resource.upload.success")),
                HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getResources(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "topicId", required = false) Long topicId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "ageGroupId", required = false) Long ageGroupId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        com.kindergarten.warehouse.dto.request.ResourceFilterRequest filterRequest = new com.kindergarten.warehouse.dto.request.ResourceFilterRequest();
        filterRequest.setKeyword(keyword);
        filterRequest.setTopicId(topicId);
        filterRequest.setCategoryId(categoryId);
        filterRequest.setAgeGroupId(ageGroupId);

        return new ResponseEntity<>(
                ApiResponse.success(resourceService.getResources(filterRequest, page, size),
                        messageService.getMessage("resource.list.success")),
                HttpStatus.OK);
    }

    @PutMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Void>> incrementViewCount(@PathVariable String id) {
        resourceService.incrementViewCount(id);
        return ResponseEntity
                .ok(ApiResponse.success(null, messageService.getMessage("resource.view.increment.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable String id) {
        resourceService.deleteResource(id);
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("resource.delete.success")));
    }
}
