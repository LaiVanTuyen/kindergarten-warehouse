package com.kindergarten.warehouse.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class YoutubeServiceTest {
    private final YoutubeService service=new YoutubeService();
    @Test void parsesSupportedLinksWithoutLosingTheVideoId() {
        for(String url:java.util.List.of("https://youtube.com/watch?feature=share&v=M7lc1UVf-VE&t=3",
            "https://youtu.be/M7lc1UVf-VE?si=abc", "https://www.youtube.com/shorts/M7lc1UVf-VE",
            "https://www.youtube.com/embed/M7lc1UVf-VE", "https://m.youtube.com/live/M7lc1UVf-VE"))
            assertEquals("M7lc1UVf-VE",service.extractVideoId(url),url);
    }
    @Test void rejectsLookalikeHostsAndInvalidIdentifiers() {
        for(String url:java.util.List.of("https://evil-youtube.com/watch?v=M7lc1UVf-VE",
            "https://youtube.com.evil.test/embed/M7lc1UVf-VE", "javascript:alert(1)",
            "https://youtube.com/watch?v=x", "https://example.com/https://youtu.be/M7lc1UVf-VE"))
            assertNull(service.extractVideoId(url),url);
    }
}
