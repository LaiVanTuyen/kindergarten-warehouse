package com.kindergarten.warehouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindergarten.warehouse.dto.response.PreviewResponse;
import com.kindergarten.warehouse.entity.*;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResourcePreviewServiceTest {
    @TempDir Path directory;
    ResourceRepository repository;
    PreviewRenderer renderer;
    ResourcePreviewService service;
    Resource resource;

    @BeforeEach void setup() {
        repository = mock(ResourceRepository.class);
        renderer = mock(PreviewRenderer.class);
        service = new ResourcePreviewService(repository, new ResourceAccessGuard(), renderer,
                new ObjectMapper(), directory.toString());
        Category category = Category.builder().visibility(Visibility.PUBLIC).isDeleted(false).build();
        Topic topic = Topic.builder().visibility(Visibility.PUBLIC).isDeleted(false).category(category).build();
        resource = Resource.builder().id("sample").topic(topic).visibility(Visibility.PUBLIC)
                .status(ResourceStatus.APPROVED).resourceType(ResourceType.FILE)
                .fileExtension("pdf").fileUrl("private/file.pdf").build();
        resource.setCreatedBy(10L);
        when(repository.findForPreviewAccess("sample")).thenReturn(Optional.of(resource));
    }

    @AfterEach void cleanup() { service.close(); }

    @Test void guestCannotProbePrivateOrInternalPreview() {
        for (Visibility visibility : Set.of(Visibility.PRIVATE, Visibility.INTERNAL)) {
            resource.setVisibility(visibility);
            assertThrows(AppException.class, () -> service.describe("sample", Viewer.guest()));
            assertThrows(AppException.class, () -> service.page("sample", 1, Viewer.guest()));
        }
        verifyNoInteractions(renderer);
    }

    @Test void draftDeletedAndArchivedDoNotGenerate() {
        resource.setStatus(ResourceStatus.DRAFT);
        assertThrows(AppException.class, () -> service.describe("sample", Viewer.of(10L, Set.of(Role.TEACHER))));
        resource.setStatus(ResourceStatus.ARCHIVED);
        assertThrows(AppException.class, () -> service.describe("sample", Viewer.guest()));
        resource.setStatus(ResourceStatus.APPROVED);
        resource.setIsDeleted(true);
        assertThrows(AppException.class, () -> service.describe("sample", Viewer.guest()));
        verifyNoInteractions(renderer);
    }

    @Test void unsupportedDoesNotRunConversion() {
        resource.setFileExtension("zip");
        assertEquals("UNSUPPORTED", service.describe("sample", Viewer.guest()).status());
        verifyNoInteractions(renderer);
    }

    @Test void publicPreviewIsDeduplicatedAndCachedButPermissionIsNot() throws Exception {
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        when(renderer.render(anyString(), eq("pdf"), any())).thenAnswer(invocation -> {
            started.countDown();
            finish.await(5, TimeUnit.SECONDS);
            Files.write(((Path) invocation.getArgument(2)).resolve("1.jpg"), new byte[]{1, 2});
            return new PreviewResponse("READY", 1, 1, false, "ready");
        });
        assertEquals("PROCESSING", service.describe("sample", Viewer.guest()).status());
        assertTrue(started.await(5, TimeUnit.SECONDS));
        service.describe("sample", Viewer.guest());
        finish.countDown();
        waitReady();
        assertTrue(service.page("sample", 1, Viewer.guest()).exists());
        assertThrows(AppException.class, () -> service.page("sample", 0, Viewer.guest()));
        assertThrows(AppException.class, () -> service.page("sample", 2, Viewer.guest()));
        resource.setVisibility(Visibility.PRIVATE);
        assertThrows(AppException.class, () -> service.page("sample", 1, Viewer.guest()));
        verify(renderer, times(1)).render(anyString(), eq("pdf"), any());
    }

    @Test void replacementCannotUseOldPagesAndRestartUsesManifest() throws Exception {
        when(renderer.render(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Files.write(((Path) invocation.getArgument(2)).resolve("1.jpg"), new byte[]{1});
            return new PreviewResponse("READY", 1, 1, false, "ready");
        });
        service.describe("sample", Viewer.guest());
        waitReady();
        service.close();
        service = new ResourcePreviewService(repository, new ResourceAccessGuard(), renderer,
                new ObjectMapper(), directory.toString());
        assertEquals("READY", service.describe("sample", Viewer.guest()).status());
        resource.setFileUrl("private/replacement.pdf");
        assertThrows(AppException.class, () -> service.page("sample", 1, Viewer.guest()));
        verify(renderer, times(1)).render(anyString(), anyString(), any());
    }

    @Test void failedConversionIsNegativeCached() throws Exception {
        when(renderer.render(anyString(), anyString(), any())).thenThrow(new java.io.IOException("broken"));
        service.describe("sample", Viewer.guest());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!"FAILED".equals(service.describe("sample", Viewer.guest()).status())) {
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(10);
        }
        service.describe("sample", Viewer.guest());
        verify(renderer, times(1)).render(anyString(), anyString(), any());
    }

    private void waitReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!"READY".equals(service.describe("sample", Viewer.guest()).status())) {
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(10);
        }
    }
}
