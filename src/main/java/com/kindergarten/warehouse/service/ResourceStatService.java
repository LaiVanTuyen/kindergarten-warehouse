package com.kindergarten.warehouse.service;

import java.util.Collection;
import java.util.Map;

public interface ResourceStatService {
    void incrementViewCount(String resourceId, String ipAddress);

    void incrementDownloadCount(String resourceId);

    long getPendingViewCount(String resourceId);

    long getPendingDownloadCount(String resourceId);

    Map<String, Long> getPendingViewCounts(Collection<String> resourceIds);

    Map<String, Long> getPendingDownloadCounts(Collection<String> resourceIds);
}
