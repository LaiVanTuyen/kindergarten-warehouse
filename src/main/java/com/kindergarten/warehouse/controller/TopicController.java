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

        /**
         * API_CONTRACT_V2 §0.5: whitelist sort RIENG cua endpoint nay.
         * KHONG co {@code resourceCount} — xem ghi chu o CategoryController.
         */
        private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of(
                        "id", "name", "slug", "visibility", "createdAt", "updatedAt");

        private static final org.springframework.data.domain.Sort DEFAULT_SORT =
                        org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "id");

        private final TopicService topicService;
        private final MessageService messageService;
        private final com.kindergarten.warehouse.security.ViewerResolver viewerResolver;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<TopicResponse>>> getAllTopics(
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(defaultValue = "false") boolean deleted,
                        @RequestParam(required = false) String keyword,
                        @org.springframework.data.web.PageableDefault(size = 10, sort = "id",
                                        direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable requestedPageable,
                        org.springframework.security.core.Authentication authentication) {

                Pageable pageable = PageableUtils.sanitize(requestedPageable, SORT_FIELDS, DEFAULT_SORT);

                return ResponseEntity
                                .ok(ApiResponse.success(
                                                topicService.getAllTopics(categoryId, deleted, keyword, pageable,
                                                                viewerResolver.resolve(authentication)),
                                                messageService.getMessage("topic.list.success")));
        }

        @PostMapping
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<TopicResponse>> createTopic(
                        @RequestBody @Valid TopicRequest topicRequest) {
                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.success(
                                                topicService.createTopic(topicRequest, topicRequest.getCategoryId()),
                                                messageService.getMessage("topic.create.success")));
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
