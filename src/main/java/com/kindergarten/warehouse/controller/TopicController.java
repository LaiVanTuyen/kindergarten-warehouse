package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.dto.request.TopicRequest;

import com.kindergarten.warehouse.service.MessageService;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final MessageService messageService;

    public TopicController(TopicService topicService, MessageService messageService) {
        this.topicService = topicService;
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicResponse>>> getAllTopics(
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity
                .ok(ApiResponse.success(topicService.getAllTopics(categoryId),
                        messageService.getMessage("topic.list.success")));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<TopicResponse>> createTopic(
            @RequestBody @jakarta.validation.Valid TopicRequest topicRequest,
            @RequestParam Long categoryId) {
        return new ResponseEntity<>(
                ApiResponse.success(topicService.createTopic(topicRequest, categoryId),
                        messageService.getMessage("topic.create.success")),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<TopicResponse>> updateTopic(@PathVariable Long id,
            @RequestBody @jakarta.validation.Valid TopicRequest topicRequest) {
        return ResponseEntity
                .ok(ApiResponse.success(topicService.updateTopic(id, topicRequest),
                        messageService.getMessage("topic.update.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(@PathVariable Long id) {
        topicService.deleteTopic(id);
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("topic.delete.success")));
    }
}
