package com.kindergarten.warehouse.util;

import com.kindergarten.warehouse.entity.FileType;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceFileTypeRegistryTest {

    @Test
    void supportsPowerPointAndPreservesItsContentType() {
        ResourceFileTypeRegistry.FileMetadata metadata = ResourceFileTypeRegistry.requireSupported(".PPTX");

        assertEquals(FileType.POWERPOINT, metadata.fileType());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadata.contentType());
    }

    @Test
    void supportsImageResources() {
        assertEquals(FileType.IMAGE, ResourceFileTypeRegistry.requireSupported("webp").fileType());
        assertEquals("image/jpeg", ResourceFileTypeRegistry.contentTypeOrDefault("jpeg"));
    }

    @Test
    void rejectsUnsupportedExtension() {
        AppException exception = assertThrows(AppException.class,
                () -> ResourceFileTypeRegistry.requireSupported("exe"));

        assertEquals(ErrorCode.FILE_TYPE_INVALID, exception.getErrorCode());
    }
}
