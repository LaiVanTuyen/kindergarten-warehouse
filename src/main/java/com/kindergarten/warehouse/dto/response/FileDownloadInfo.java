package com.kindergarten.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

/**
 * DTO for file download information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadInfo {
    private InputStream inputStream;
    private String fileName;
    private String contentType;
    private long fileSize;
}

