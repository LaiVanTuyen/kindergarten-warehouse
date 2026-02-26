package com.kindergarten.warehouse.service;

public interface ResourceStatService {
    void incrementViewCount(String resourceId, String ipAddress);

    void incrementDownloadCount(String resourceId);

    long getPendingViewCount(String resourceId);

    long getPendingDownloadCount(String resourceId);
}
