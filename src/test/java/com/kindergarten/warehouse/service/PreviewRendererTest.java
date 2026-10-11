package com.kindergarten.warehouse.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreviewRendererTest {
    @TempDir Path directory;

    @Test void webpDecoderIsInstalled() {
        assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext());
    }

    @Test void pdfRendersDistinctPagesWithoutPublishingOriginal() throws Exception {
        byte[] pdf = demoPdf();
        MinioStorageService storage = mock(MinioStorageService.class);
        when(storage.getObject("private.pdf")).thenReturn(new ByteArrayInputStream(pdf));
        var result = new PreviewRenderer(storage, "unused").render("private.pdf", "pdf", directory);
        assertEquals("READY", result.status());
        assertEquals(3, result.pageCount());
        assertNotNull(ImageIO.read(directory.resolve("1.jpg").toFile()));
        assertNotNull(ImageIO.read(directory.resolve("3.jpg").toFile()));
        assertFalse(Files.exists(directory.resolve("document.pdf")));
        // Reproducible synthetic fixture for HTTP/UI acceptance, not real school data.
        Files.write(Path.of("target/demo-preview.pdf"), pdf);
    }

    @Test void imageProducesRasterPreview() throws Exception {
        BufferedImage image = new BufferedImage(60, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MinioStorageService storage = mock(MinioStorageService.class);
        when(storage.getObject("image.png")).thenReturn(new ByteArrayInputStream(bytes.toByteArray()));
        var result = new PreviewRenderer(storage, "unused").render("image.png", "png", directory);
        assertEquals(1, result.totalPages());
        assertTrue(Files.isRegularFile(directory.resolve("1.jpg")));
    }

    @Test void malformedPdfFailsInsteadOfReturningBlankSuccess() throws Exception {
        MinioStorageService storage = mock(MinioStorageService.class);
        when(storage.getObject("broken.pdf")).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3}));
        assertThrows(IOException.class,
                () -> new PreviewRenderer(storage, "unused").render("broken.pdf", "pdf", directory));
    }

    private byte[] demoPdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Color[] colors = {new Color(21, 127, 114), new Color(235, 155, 46), new Color(103, 92, 172)};
            String[] titles = {"LEARNING THROUGH PLAY", "COLORS AND SHAPES", "LET'S COUNT TO FIVE"};
            for (int index = 0; index < 3; index++) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.setNonStrokingColor(colors[index]);
                    content.addRect(0, 690, 595, 152);
                    content.fill();
                    content.beginText();
                    content.setNonStrokingColor(Color.WHITE);
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 26);
                    content.newLineAtOffset(38, 756);
                    content.showText(titles[index]);
                    content.endText();
                    for (int shape = 0; shape <= index + 2; shape++) {
                        content.setNonStrokingColor(colors[index]);
                        content.addRect(45 + shape * 92, 490, 60, 60);
                        content.fill();
                    }
                    content.beginText();
                    content.setNonStrokingColor(Color.DARK_GRAY);
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 17);
                    content.newLineAtOffset(40, 390);
                    content.showText("Teacher activity: observe, discuss and practice together.");
                    content.newLineAtOffset(0, -40);
                    content.showText("Synthetic demo material - no real children's information.");
                    content.endText();
                }
            }
            pdf.save(bytes);
            return bytes.toByteArray();
        }
    }
}
