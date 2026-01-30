package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.TopicRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.TopicService;
import com.kindergarten.warehouse.util.PageableUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

        private final TopicService topicService;
        private final MessageService messageService;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<TopicResponse>>> getAllTopics(
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(defaultValue = "false") boolean deleted,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "id") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortDir) {

                Pageable pageable = PageableUtils.createPageable(page, size, sortBy, sortDir);

                return ResponseEntity
                                .ok(ApiResponse.success(
                                                topicService.getAllTopics(categoryId, deleted, keyword, pageable),
                                                messageService.getMessage("topic.list.success")));
        }

        @PostMapping
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<TopicResponse>> createTopic(
                        @RequestBody @Valid TopicRequest topicRequest) {
                return new ResponseEntity<>(
                                ApiResponse.success(
                                                topicService.createTopic(topicRequest, topicRequest.getCategoryId()),
                                                messageService.getMessage("topic.create.success")),
                                HttpStatus.CREATED);
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<TopicResponse>> updateTopic(@PathVariable Long id,
                        @RequestBody @Valid TopicRequest topicRequest) {
                UpdateResult<TopicResponse> updateResult = topicService.updateTopic(id, topicRequest);
                return ResponseEntity
                                .ok(ApiResponse.success(updateResult.getResult(),
                                                messageService.getMessage(updateResult.getMessageKey())));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> deleteTopic(@PathVariable Long id,
                        @RequestParam(defaultValue = "false") boolean hard) {
                topicService.deleteTopic(id, hard);
                return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("topic.delete.success")));
        }

        @PatchMapping("/{id}/restore")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<TopicResponse>> restoreTopic(@PathVariable Long id) {
                return ResponseEntity.ok(ApiResponse.success(topicService.restoreTopic(id),
                                messageService.getMessage("topic.restore.success", "Topic restored successfully")));
        }
}
