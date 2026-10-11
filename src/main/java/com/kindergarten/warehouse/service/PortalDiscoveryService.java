package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.AuthorProfileResponse;
import com.kindergarten.warehouse.dto.response.DownloadHistoryResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.dto.response.TagCountResponse;
import com.kindergarten.warehouse.entity.DownloadHistory;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.DownloadHistoryRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.Viewer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** tailieu.vn-style discovery: author pages, tag cloud and per-user download history. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalDiscoveryService {

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final DownloadHistoryRepository downloadHistoryRepository;
    private final ResourceService resourceService;

    /** Only active teachers/admins have a public page; everyone else is a 404 so ids can't be probed. */
    @Transactional(readOnly = true)
    public AuthorProfileResponse getAuthor(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> u.getRoles() != null
                        && (u.getRoles().contains(Role.TEACHER) || u.getRoles().contains(Role.ADMIN)))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        List<Object[]> rows = resourceRepository.findPublicStatsByAuthor(userId);
        Object[] stats = rows.isEmpty() ? new Object[] { 0L, 0L, 0L } : rows.get(0);
        return new AuthorProfileResponse(
                user.getId(),
                // Never fall back to username: it is a login identifier, not a display name.
                user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Giáo viên",
                user.getAvatarUrl(),
                user.getBio(),
                user.getCreatedAt(),
                toLong(stats[0]),
                toLong(stats[1]),
                toLong(stats[2]));
    }

    @Transactional(readOnly = true)
    public List<TagCountResponse> getPopularTags(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return resourceRepository.findPopularTags(PageRequest.of(0, size)).stream()
                .map(row -> new TagCountResponse((String) row[0], toLong(row[1])))
                .toList();
    }

    /** Called after the file stream is opened; failures are logged, never block the download. */
    @Transactional
    public void recordDownload(Long userId, String resourceId) {
        if (userId == null || resourceId == null) {
            return;
        }
        try {
            downloadHistoryRepository.recordDownload(userId, resourceId);
        } catch (Exception e) {
            log.warn("Failed to record download history for user {} resource {}: {}",
                    userId, resourceId, e.getMessage());
        }
    }

    /**
     * Visibility is applied in SQL (join to resources) before pagination and count,
     * so pages are full and totals only include resources the viewer can still see.
     */
    @Transactional(readOnly = true)
    public Page<DownloadHistoryResponse> getDownloadHistory(Viewer viewer, Pageable pageable) {
        Long userId = viewer.userId();
        org.springframework.data.jpa.domain.Specification<DownloadHistory> visibleHistory = (root, query, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                com.kindergarten.warehouse.security.ResourceVisibilitySpecifications
                        .portalVisiblePredicate(root.join("resource"), cb, viewer));
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "lastDownloadedAt"));
        Page<DownloadHistory> history = downloadHistoryRepository.findAll(visibleHistory, sorted);
        List<String> ids = history.getContent().stream().map(DownloadHistory::getResourceId).toList();
        Map<String, ResourceResponse> resources = resourceService.getPortalResourcesByIds(ids, viewer).stream()
                .collect(Collectors.toMap(ResourceResponse::getId, Function.identity()));
        List<DownloadHistoryResponse> content = history.getContent().stream()
                .filter(h -> resources.containsKey(h.getResourceId()))
                .map(h -> new DownloadHistoryResponse(resources.get(h.getResourceId()),
                        h.getDownloadCount() == null ? 1 : h.getDownloadCount(), h.getLastDownloadedAt()))
                .toList();
        return new PageImpl<>(content, sorted, history.getTotalElements());
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
