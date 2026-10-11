package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.PreviewResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class PreviewRenderer {
    private final MinioStorageService storage;
    private final String office;
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final int MAX_PAGES = 100;

    public PreviewRenderer(MinioStorageService storage,
            @Value("${app.preview.office-command:libreoffice}") String office) {
        this.storage = storage;
        this.office = office;
    }

    public PreviewResponse render(String source, String extension, Path output) throws Exception {
        Path workspace = Files.createTempDirectory("warehouse-preview-");
        try {
            Path input = workspace.resolve("document." + extension);
            try (InputStream in = storage.getObject(source); OutputStream out = Files.newOutputStream(input)) {
                byte[] bytes = new byte[8192];
                long length = 0;
                int count;
                while ((count = in.read(bytes)) != -1) {
                    length += count;
                    if (length > MAX_BYTES) throw new IOException("Document exceeds preview size limit");
                    out.write(bytes, 0, count);
                }
            }
            if (Set.of("png", "jpg", "jpeg", "webp").contains(extension)) {
                renderImage(input, output.resolve("1.jpg"));
                return new PreviewResponse("READY", 1, 1, false, "Bản xem trước");
            }
            Path pdf = extension.equals("pdf") ? input : convertOffice(input, workspace);
            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                    throw new IOException("Protected document");
                }
                int total = document.getNumberOfPages();
                if (total < 1) throw new IOException("Empty document");
                PDFRenderer renderer = new PDFRenderer(document);
                renderer.setSubsamplingAllowed(true);
                int pages = Math.min(total, MAX_PAGES);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
                long outputBytes = 0;
                for (int index = 0; index < pages; index++) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (System.nanoTime() > deadline) throw new IOException("Rendering timeout");
                    var box = document.getPage(index).getCropBox();
                    float edge = Math.max(box.getWidth(), box.getHeight());
                    if (!Float.isFinite(edge) || edge <= 0) throw new IOException("Invalid page bounds");
                    float scale = Math.min(1.6f, 1600f / edge);
                    BufferedImage image = renderer.renderImage(index, scale, org.apache.pdfbox.rendering.ImageType.RGB);
                    writePage(image, output.resolve((index + 1) + ".jpg"));
                    outputBytes += Files.size(output.resolve((index + 1) + ".jpg"));
                    if (outputBytes > MAX_BYTES) throw new IOException("Preview exceeds output limit");
                    image.flush();
                }
                return new PreviewResponse("READY", pages, total, total > pages,
                        total > pages ? "Xem trước 100 trang đầu. Đăng nhập để tải tài liệu đầy đủ." : "Bản xem trước");
            }
        } finally {
            // Only delete the exact temp directory created by this renderer, never a user path.
            try (var paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private Path convertOffice(Path input, Path workspace) throws Exception {
        Path profile = Files.createDirectory(workspace.resolve("profile"));
        Files.createDirectories(profile.resolve("user"));
        Files.writeString(profile.resolve("user/registrymodifications.xcu"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <oor:items xmlns:oor="http://openoffice.org/2001/registry">
                <item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item>
                </oor:items>
                """);
        ProcessBuilder command = new ProcessBuilder(office, "-env:UserInstallation=" + profile.toUri(),
                "--headless", "--norestore", "--nodefault", "--convert-to", "pdf",
                "--outdir", workspace.toString(), input.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        // A converter subprocess does not need application DB/JWT/storage secrets.
        String systemPath = command.environment().getOrDefault("PATH", "/usr/bin:/bin");
        command.environment().clear();
        command.environment().put("PATH", systemPath);
        command.environment().put("HOME", workspace.toString());
        command.environment().put("LANG", "C.UTF-8");
        Process process = command.start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) throw new IOException("Conversion timeout");
            Path pdf = workspace.resolve("document.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf) || Files.size(pdf) > MAX_BYTES) {
                throw new IOException("Office conversion failed");
            }
            return pdf;
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private void renderImage(Path input, Path output) throws IOException {
        try (var stream = ImageIO.createImageInputStream(input.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Image decoder unavailable");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 40_000_000) {
                    throw new IOException("Image exceeds pixel limit");
                }
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 1600);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage image = reader.read(0, parameters);
                writePage(image, output);
                image.flush();
            } finally {
                reader.dispose();
            }
        }
    }

    private void writePage(BufferedImage source, Path output) throws IOException {
        double scale = Math.min(1, 1600.0 / Math.max(source.getWidth(), source.getHeight()));
        BufferedImage page = new BufferedImage(Math.max(1, (int) (source.getWidth() * scale)),
                Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = page.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, page.getWidth(), page.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, page.getWidth(), page.getHeight(), null);
            // Raster previews are not originals. This is attribution, not DRM.
            graphics.setColor(new Color(90, 100, 110));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            graphics.drawString("KHO HOC LIEU • BAN XEM TRUOC", 12, page.getHeight() - 12);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(page, "jpg", output.toFile())) throw new IOException("JPEG encoder unavailable");
        page.flush();
    }
}
