package com.kindergarten.warehouse.util;

import com.kindergarten.warehouse.entity.FileType;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;

import java.util.Locale;
import java.util.Map;

public final class ResourceFileTypeRegistry {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final Map<String, FileMetadata> SUPPORTED_FILES = Map.ofEntries(
            Map.entry("mp4", new FileMetadata(FileType.VIDEO, "video/mp4")),
            Map.entry("mov", new FileMetadata(FileType.VIDEO, "video/quicktime")),
            Map.entry("avi", new FileMetadata(FileType.VIDEO, "video/x-msvideo")),
            Map.entry("doc", new FileMetadata(FileType.DOCUMENT, "application/msword")),
            Map.entry("docx", new FileMetadata(FileType.DOCUMENT,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", new FileMetadata(FileType.EXCEL, "application/vnd.ms-excel")),
            Map.entry("xlsx", new FileMetadata(FileType.EXCEL,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("pdf", new FileMetadata(FileType.PDF, "application/pdf")),
            Map.entry("ppt", new FileMetadata(FileType.POWERPOINT, "application/vnd.ms-powerpoint")),
            Map.entry("pptx", new FileMetadata(FileType.POWERPOINT,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("jpg", new FileMetadata(FileType.IMAGE, "image/jpeg")),
            Map.entry("jpeg", new FileMetadata(FileType.IMAGE, "image/jpeg")),
            Map.entry("png", new FileMetadata(FileType.IMAGE, "image/png")),
            Map.entry("webp", new FileMetadata(FileType.IMAGE, "image/webp")));

    private ResourceFileTypeRegistry() {
    }

    public static FileMetadata requireSupported(String extension) {
        FileMetadata metadata = SUPPORTED_FILES.get(normalizeExtension(extension));
        if (metadata == null) {
            throw new AppException(ErrorCode.FILE_TYPE_INVALID);
        }
        return metadata;
    }

    public static String contentTypeOrDefault(String extension) {
        FileMetadata metadata = SUPPORTED_FILES.get(normalizeExtension(extension));
        return metadata != null ? metadata.contentType() : DEFAULT_CONTENT_TYPE;
    }

    public static String normalizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    public record FileMetadata(FileType fileType, String contentType) {
    }
}
