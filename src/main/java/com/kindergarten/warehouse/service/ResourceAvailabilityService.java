package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.Viewer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Resolves resource references held by collections and lesson plans for one viewer.
 *
 * <p>Uses exactly the rule of the resource detail page ({@link ResourceAccessGuard}), so a
 * reference shows a resource if and only if this viewer could open that resource directly.
 * Anything else is "unavailable" and its title is not exposed.</p>
 */
@Service
@RequiredArgsConstructor
public class ResourceAvailabilityService {

    private final ResourceRepository resourceRepository;
    private final ResourceAccessGuard resourceAccessGuard;
    private final ResourceMapper resourceMapper;
    private final ResourceStatService resourceStatService;

    /** Viewable entities keyed by id; ids the viewer can't open are absent. */
    @Transactional(readOnly = true)
    public Map<String, Resource> viewable(Collection<String> ids, Viewer viewer) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Resource> visible = new HashMap<>();
        for (Resource resource : resourceRepository.findAllWithDetailsByIdIn(new HashSet<>(ids))) {
            try {
                visible.put(resource.getId(), resourceAccessGuard.requireViewable(resource, viewer));
            } catch (AppException hidden) {
                // Deleted, archived, taken down or not visible to this viewer.
            }
        }
        return visible;
    }

    /** Same as {@link #viewable} but mapped to responses, with Redis counters fetched in two batch calls. */
    @Transactional(readOnly = true)
    public Map<String, ResourceResponse> viewableResponses(Collection<String> ids, Viewer viewer) {
        Map<String, Resource> visible = viewable(ids, viewer);
        if (visible.isEmpty()) {
            return Map.of();
        }
        List<String> visibleIds = new ArrayList<>(visible.keySet());
        Map<String, Long> pendingViews = resourceStatService.getPendingViewCounts(visibleIds);
        Map<String, Long> pendingDownloads = resourceStatService.getPendingDownloadCounts(visibleIds);
        Map<String, ResourceResponse> responses = new HashMap<>();
        visible.forEach((id, resource) -> responses.put(id, resourceMapper.toResponse(resource, false,
                pendingViews.getOrDefault(id, 0L), pendingDownloads.getOrDefault(id, 0L))));
        return responses;
    }
}
