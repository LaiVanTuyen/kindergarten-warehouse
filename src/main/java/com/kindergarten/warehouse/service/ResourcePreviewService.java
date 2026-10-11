package com.kindergarten.warehouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindergarten.warehouse.dto.response.PreviewResponse;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.ResourceType;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.Viewer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;

/** Single-instance, bounded lazy conversion cache. Derived pages are PRIVATE.
 * Cache identity includes the file URL; replacement cannot serve the old pages.
 * Permission is never cached. A restart safely reschedules incomplete jobs.
 */
@Service
@Slf4j
public class ResourcePreviewService {
    private static final Set<String> SUPPORTED = Set.of("pdf", "doc", "docx", "ppt", "pptx",
            "xls", "xlsx", "png", "jpg", "jpeg", "webp");
    private final ResourceRepository resources;
    private final ResourceAccessGuard guard;
    private final PreviewRenderer renderer;
    private final ObjectMapper json;
    private final Path root;
    private final ConcurrentMap<String, Boolean> pending = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "document-preview");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public ResourcePreviewService(ResourceRepository resources, ResourceAccessGuard guard,
            PreviewRenderer renderer, ObjectMapper json,
            @Value("${app.preview.cache-dir:/tmp/warehouse-previews}") String directory) {
        this.resources = resources;
        this.guard = guard;
        this.renderer = renderer;
        this.json = json;
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public PreviewResponse describe(String id, Viewer viewer) {
        Resource resource = requirePreviewable(id, viewer);
        String extension = extension(resource);
        if (!SUPPORTED.contains(extension)) {
            return new PreviewResponse("UNSUPPORTED", 0, 0, false,
                    "Định dạng này chưa hỗ trợ xem trước. Bạn có thể tải tệp sau khi đăng nhập.");
        }
        Path directory = directory(resource);
        PreviewResponse cached = readManifest(directory);
        if (cached != null) return cached;
        try {
            Files.createDirectories(root);
            if (!Files.isWritable(root) || Files.getFileStore(root).getUsableSpace() < 128L * 1024 * 1024) {
                return new PreviewResponse("BUSY", 0, 0, false,
                        "Kho xem trước đang thiếu dung lượng. Vui lòng liên hệ quản trị viên.");
            }
        } catch (java.io.IOException ex) {
            return new PreviewResponse("FAILED", 0, 0, false,
                    "Chưa truy cập được kho xem trước. Vui lòng liên hệ quản trị viên.");
        }
        String key = directory.getFileName().toString();
        if (pending.putIfAbsent(key, true) == null) {
            // A worker may publish its manifest and release pending after our first
            // cache read. Recheck while owning the key before scheduling another job.
            PreviewResponse completed = readManifest(directory);
            if (completed != null) {
                pending.remove(key);
                return completed;
            }
            // Pass an immutable snapshot; the worker never dereferences detached JPA relations.
            String source = resource.getFileUrl();
            try {
                executor.execute(() -> generate(key, directory, source, extension));
            } catch (RejectedExecutionException ex) {
                pending.remove(key);
                return new PreviewResponse("BUSY", 0, 0, false,
                        "Hệ thống đang xử lý nhiều tài liệu. Vui lòng thử lại sau ít phút.");
            }
        }
        return new PreviewResponse("PROCESSING", 0, 0, false,
                "Đang chuẩn bị bản xem trước. Bạn không cần tải tệp gốc.");
    }

    public FileSystemResource page(String id, int page, Viewer viewer) {
        Resource resource = requirePreviewable(id, viewer);
        Path directory = directory(resource);
        PreviewResponse manifest = readManifest(directory);
        if (manifest == null || !"READY".equals(manifest.status())
                || page < 1 || page > manifest.pageCount()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Path image = directory.resolve(page + ".jpg");
        if (!Files.isRegularFile(image)) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return new FileSystemResource(image);
    }

    private Resource requirePreviewable(String id, Viewer viewer) {
        Resource resource = guard.requireViewable(resources.findForPreviewAccess(id).orElse(null), viewer);
        if (resource.getStatus() == ResourceStatus.DRAFT || resource.getResourceType() != ResourceType.FILE
                || resource.getFileUrl() == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return resource;
    }

    private String extension(Resource resource) {
        String value = resource.getFileExtension();
        return value == null ? "" : value.replace(".", "").toLowerCase(Locale.ROOT);
    }

    private Path directory(Resource resource) {
        try {
            String identity = "v1:" + resource.getId() + ":" + resource.getFileUrl();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
            return root.resolve(hash);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private PreviewResponse readManifest(Path directory) {
        Path manifest = directory.resolve("manifest.json");
        try {
            if (!Files.isRegularFile(manifest)) return null;
            PreviewResponse result = json.readValue(manifest.toFile(), PreviewResponse.class);
            // Negative caching prevents every poll from re-running a malformed document.
            if ("FAILED".equals(result.status()) && Files.getLastModifiedTime(manifest).toInstant()
                    .isBefore(Instant.now().minusSeconds(120))) return null;
            return result;
        } catch (Exception ex) {
            return null;
        }
    }

    private void generate(String key, Path directory, String source, String extension) {
        try {
            maintainCache(key);
            Files.createDirectories(directory);
            if (Files.getFileStore(directory).getUsableSpace() < 128L * 1024 * 1024) {
                throw new java.io.IOException("Insufficient space for preview");
            }
            PreviewResponse result;
            try {
                result = renderer.render(source, extension, directory);
            } catch (Exception ex) {
                log.warn("Preview conversion failed ({})", ex.getClass().getSimpleName());
                result = new PreviewResponse("FAILED", 0, 0, false,
                        "Không tạo được bản xem trước. Tệp có thể bị lỗi, được bảo vệ hoặc vượt giới hạn xử lý.");
            }
            Path temporary = directory.resolve("manifest.tmp");
            json.writeValue(temporary.toFile(), result);
            Files.move(temporary, directory.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            log.warn("Cannot persist preview ({})", ex.getClass().getSimpleName());
        } finally {
            pending.remove(key);
        }
    }

    /** Soft 512 MiB budget and seven-day TTL. Only converter-owned hash directories
     * are candidates, and queued/running jobs are never removed. Cache entries are
     * disposable derived data; source objects in MinIO are not touched.
     */
    private void maintainCache(String currentKey) throws java.io.IOException {
        Files.createDirectories(root);
        java.util.List<Path> entries;
        try (var paths = Files.list(root)) {
            entries = paths.filter(path -> path.getFileName().toString().matches("[a-f0-9]{64}"))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(java.util.Comparator.comparingLong(this::modifiedAt)).toList();
        }
        long bytes = 0;
        java.util.Map<Path, Long> sizes = new java.util.HashMap<>();
        for (Path entry : entries) {
            try (var paths = Files.walk(entry)) {
                long size = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .mapToLong(path -> { try { return Files.size(path); } catch (Exception ex) { return 0; } }).sum();
                sizes.put(entry, size);
                bytes += size;
            }
        }
        long expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        for (Path entry : entries) {
            String key = entry.getFileName().toString();
            if (key.equals(currentKey) || pending.containsKey(key)) continue;
            if (bytes <= 512L * 1024 * 1024 && modifiedAt(entry) >= expiry) continue;
            // NOFOLLOW_LINKS above and normalized containment keep deletion inside our cache.
            if (!entry.toAbsolutePath().normalize().startsWith(root)) continue;
            try (var paths = Files.walk(entry)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
            bytes -= sizes.getOrDefault(entry, 0L);
        }
    }

    private long modifiedAt(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ex) { return 0; }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
