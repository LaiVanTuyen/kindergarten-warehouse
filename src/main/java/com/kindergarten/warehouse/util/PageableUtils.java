package com.kindergarten.warehouse.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PageableUtils {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_PAGE_SIZE = 1;
    private static final String DEFAULT_SORT_FIELD = "id";

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "timestamp",
            "displayOrder",
            "title",
            "name",
            "status",
            "createdBy",
            "updatedBy",
            "username",
            "email",
            "fullName",
            "lastActive");

    private PageableUtils() {
    }

    public static Pageable createPageable(int page, int size, String sortBy, String sortDir) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, size));
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : DEFAULT_SORT_FIELD;

        Sort sort = Sort.Direction.ASC.name().equalsIgnoreCase(sortDir)
                ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        return PageRequest.of(safePage, safeSize, sort);
    }
}
