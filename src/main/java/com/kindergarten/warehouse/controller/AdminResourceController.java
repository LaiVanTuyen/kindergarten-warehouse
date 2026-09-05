package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.BulkResourceRequest;
import com.kindergarten.warehouse.dto.request.RejectRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.BulkOperationResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/resources")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN')")
public class AdminResourceController {

        /**
         * API_CONTRACT_V2 §0.5: whitelist sort RIENG cua endpoint nay.
         * Bon cot Admin UI cho bam sort la title, status, viewsCount, createdAt —
         * truoc day endpoint KHONG nhan tham so {@code sort} nen bam vao khong co
         * tac dung gi, trong khi UI van hien mui ten sort.
         */
        private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of(
                        "id", "title", "status", "visibility", "createdAt", "updatedAt",
                        "viewsCount", "downloadCount", "averageRating");

        private static final org.springframework.data.domain.Sort DEFAULT_SORT =
                        org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt");

        private final ResourceService resourceService;
        private final MessageService messageService;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<ResourceResponse>>> getAdminResources(
                        @ModelAttribute ResourceFilterRequest filterRequest,
                        @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt",
                                        direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable requestedPageable) {

                org.springframework.data.domain.Pageable pageable =
                                com.kindergarten.warehouse.util.PageableUtils.sanitize(
                                                requestedPageable, SORT_FIELDS, DEFAULT_SORT);

                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.getAdminResources(filterRequest, pageable),
                                                messageService.getMessage("resource.list.success")),
                                HttpStatus.OK);
        }

        @PatchMapping("/{id}/approve")
        public ResponseEntity<ApiResponse<ResourceResponse>> approveResource(
                        @PathVariable String id,
                        Principal principal) {
                return new ResponseEntity<>(
                                ApiResponse.success(resourceService.approveResource(id, principal.getName()),
                                                messageService.getMessage("resource.approve.success")),
                                HttpStatus.OK);
        }

        @PatchMapping("/{id}/reject")
        public ResponseEntity<ApiResponse<ResourceResponse>> rejectResource(
                        @PathVariable String id,
                        @Valid @RequestBody RejectRequest request,
                        Principal principal) {
                return new ResponseEntity<>(
                                ApiResponse.success(
                                                resourceService.rejectResource(id, request.getReason(),
                                                                principal.getName()),
                                                messageService.getMessage("resource.reject.success")),
                                HttpStatus.OK);
        }

        @PatchMapping("/bulk-approve")
        public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkApprove(
                        @Valid @RequestBody BulkResourceRequest request,
                        Principal principal) {
                return ResponseEntity.ok(ApiResponse.success(
                                resourceService.bulkApprove(request, principal.getName()),
                                "Bulk approve processed"));
        }

        @PatchMapping("/bulk-reject")
        public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkReject(
                        @Valid @RequestBody BulkResourceRequest request,
                        Principal principal) {
                return ResponseEntity.ok(ApiResponse.success(
                                resourceService.bulkReject(request, principal.getName()),
                                "Bulk reject processed"));
        }
}
