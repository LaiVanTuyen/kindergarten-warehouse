package com.kindergarten.warehouse.service;

public interface ResourceStatService {
    void incrementViewCount(String resourceId, String ipAddress);
    void incrementDownloadCount(String resourceId);
}
