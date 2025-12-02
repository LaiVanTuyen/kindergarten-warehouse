package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<List<com.kindergarten.warehouse.dto.response.TopicResponse>> getAllTopics() {
        return ResponseEntity.ok(topicService.getAllTopics());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<com.kindergarten.warehouse.dto.response.TopicResponse> createTopic(
            @RequestBody @jakarta.validation.Valid com.kindergarten.warehouse.dto.request.TopicRequest topicRequest,
            @RequestParam Long categoryId) {
        return new ResponseEntity<>(topicService.createTopic(topicRequest, categoryId), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<com.kindergarten.warehouse.dto.response.TopicResponse> updateTopic(@PathVariable Long id,
            @RequestBody @jakarta.validation.Valid com.kindergarten.warehouse.dto.request.TopicRequest topicRequest) {
        return ResponseEntity.ok(topicService.updateTopic(id, topicRequest));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long id) {
        topicService.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }
}
