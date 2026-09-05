package com.kindergarten.warehouse.util;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageableUtilsTest {

    private static final Set<String> ALLOWED_FIELDS = Set.of("id", "createdAt", "title");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void preservesMultipleAllowedSortOrders() {
        Pageable requested = PageRequest.of(2, 25, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("title")));

        Pageable sanitized = PageableUtils.sanitize(requested, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(2, sanitized.getPageNumber());
        assertEquals(25, sanitized.getPageSize());
        assertEquals(Sort.Direction.DESC, sanitized.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.ASC, sanitized.getSort().getOrderFor("title").getDirection());
    }

    @Test
    void capsPageSizeAtOneHundred() {
        Pageable requested = PageRequest.of(0, 500, Sort.by("id"));

        Pageable sanitized = PageableUtils.sanitize(requested, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(100, sanitized.getPageSize());
    }

    @Test
    void rejectsUnknownSortField() {
        Pageable requested = PageRequest.of(0, 10, Sort.by("password"));

        AppException exception = assertThrows(AppException.class,
                () -> PageableUtils.sanitize(requested, ALLOWED_FIELDS, DEFAULT_SORT));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }
}
