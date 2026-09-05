package com.kindergarten.warehouse.util;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PageableUtils {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_PAGE_SIZE = 1;

    private PageableUtils() {
    }

    public static Pageable sanitize(Pageable pageable, Set<String> allowedSortFields, Sort defaultSort) {
        int safePage = Math.max(0, pageable.getPageNumber());
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, pageable.getPageSize()));
        Sort safeSort = sanitizeSort(pageable.getSort(), allowedSortFields, defaultSort);
        return PageRequest.of(safePage, safeSize, safeSort);
    }

    public static Sort sanitizeSort(Sort requestedSort, Set<String> allowedSortFields, Sort defaultSort) {
        if (requestedSort == null || requestedSort.isUnsorted()) {
            return defaultSort;
        }

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : requestedSort) {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            safeOrders.add(order);
        }

        return safeOrders.isEmpty() ? defaultSort : Sort.by(safeOrders);
    }
}
