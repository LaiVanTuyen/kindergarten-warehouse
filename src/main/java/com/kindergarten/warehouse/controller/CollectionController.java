package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.CollectionRequests;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.CollectionResponses;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.CollectionService;
import com.kindergarten.warehouse.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Bộ học liệu" for teachers. See {@link CollectionService} for access rules. */
@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TEACHER', 'ADMIN')")
public class CollectionController {

    private final CollectionService collectionService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<CollectionResponses.Summary>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ok(collectionService.listMine(viewer(authentication), page, size));
    }

    @GetMapping("/shared")
    public ResponseEntity<ApiResponse<Page<CollectionResponses.Summary>>> shared(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ok(collectionService.listShared(viewer(authentication), keyword, ageGroupId, page, size));
    }

    /** My collections with a flag telling whether each already holds {@code resourceId}. */
    @GetMapping("/me/membership")
    public ResponseEntity<ApiResponse<List<CollectionResponses.Membership>>> membership(
            @RequestParam String resourceId, Authentication authentication) {
        return ok(collectionService.membershipFor(resourceId, viewer(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CollectionResponses.Detail>> get(
            @PathVariable Long id, Authentication authentication) {
        return ok(collectionService.get(id, viewer(authentication)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CollectionResponses.Summary>> create(
            @Valid @RequestBody CollectionRequests.Upsert request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                collectionService.create(request, viewer(authentication)), message()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CollectionResponses.Summary>> update(
            @PathVariable Long id, @Valid @RequestBody CollectionRequests.Upsert request,
            Authentication authentication) {
        return ok(collectionService.update(id, request, viewer(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, Authentication authentication) {
        collectionService.delete(id, viewer(authentication));
        return ok(null);
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<ApiResponse<CollectionResponses.Summary>> copy(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                collectionService.copy(id, viewer(authentication)), message()));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<CollectionResponses.Item>> addItem(
            @PathVariable Long id, @Valid @RequestBody CollectionRequests.AddItem request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                collectionService.addItem(id, request, viewer(authentication)), message()));
    }

    @PatchMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> updateItem(
            @PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody CollectionRequests.UpdateItem request, Authentication authentication) {
        collectionService.updateItem(id, itemId, request, viewer(authentication));
        return ok(null);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable Long id, @PathVariable Long itemId, Authentication authentication) {
        collectionService.removeItem(id, itemId, viewer(authentication));
        return ok(null);
    }

    @PutMapping("/{id}/items/order")
    public ResponseEntity<ApiResponse<Void>> reorder(
            @PathVariable Long id, @Valid @RequestBody CollectionRequests.Reorder request,
            Authentication authentication) {
        collectionService.reorder(id, request, viewer(authentication));
        return ok(null);
    }

    private Viewer viewer(Authentication authentication) {
        return viewerResolver.resolve(authentication);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok(ApiResponse.success(body, message()));
    }

    private String message() {
        return messageService.getMessage("collection.success");
    }
}
