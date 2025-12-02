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

import java.util.UUID;
import com.kindergarten.warehouse.dto.response.ResourceResponse;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<ResourceResponse> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("topicId") Long topicId,
            Principal principal) {

        return new ResponseEntity<>(
                resourceService.uploadResource(file, title, description, topicId, principal.getName()),
                HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<ResourceResponse>> getResources(
            @RequestParam(value = "topicId", required = false) Long topicId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        return new ResponseEntity<>(resourceService.getResources(topicId, categoryId, page, size), HttpStatus.OK);
    }

    @PutMapping("/{id}/view")
    public ResponseEntity<Void> incrementViewCount(@PathVariable UUID id) {
        resourceService.incrementViewCount(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        resourceService.deleteResource(id);
        return ResponseEntity.noContent().build();
    }
}
