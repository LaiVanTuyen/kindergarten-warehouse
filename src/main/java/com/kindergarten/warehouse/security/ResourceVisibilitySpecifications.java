package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Visibility;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** JPA counterpart of {@link VisibilityPolicy} for paged Portal queries. */
public final class ResourceVisibilitySpecifications {

    private ResourceVisibilitySpecifications() {
    }

    /** Filters in SQL before pagination so content, totals and page boundaries agree. */
    public static Specification<Resource> portalVisibleTo(Viewer viewer) {
        if (viewer == null) {
            throw new IllegalArgumentException("Viewer must not be null; use Viewer.guest()");
        }

        return (root, query, cb) -> {
            List<Predicate> required = new ArrayList<>();
            required.add(cb.isFalse(root.get("isDeleted")));
            required.add(cb.equal(root.get("status"), ResourceStatus.APPROVED));
            required.add(cb.isFalse(root.get("topic").get("isDeleted")));
            required.add(cb.isFalse(root.get("topic").get("category").get("isDeleted")));

            if (!viewer.isAdmin()) {
                Predicate visibilityGate;
                if (!viewer.isAuthenticated()) {
                    visibilityGate = cb.and(
                            cb.equal(root.get("visibility"), Visibility.PUBLIC),
                            cb.equal(root.get("topic").get("visibility"), Visibility.PUBLIC),
                            cb.equal(root.get("topic").get("category").get("visibility"), Visibility.PUBLIC));
                } else {
                    Predicate publicOrInternalChain = cb.and(
                            root.get("visibility").in(Visibility.PUBLIC, Visibility.INTERNAL),
                            root.get("topic").get("visibility").in(Visibility.PUBLIC, Visibility.INTERNAL),
                            root.get("topic").get("category").get("visibility")
                                    .in(Visibility.PUBLIC, Visibility.INTERNAL));
                    visibilityGate = cb.or(
                            cb.equal(root.get("createdBy"), viewer.userId()),
                            publicOrInternalChain);
                }
                required.add(visibilityGate);
            }

            return cb.and(required.toArray(Predicate[]::new));
        };
    }
}
