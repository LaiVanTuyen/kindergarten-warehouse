package com.kindergarten.warehouse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class YoutubeService {

    @Value("${youtube.api.key:}")
    private String apiKey;

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?<=watch\\?v=|/videos/|embed/|youtu\\.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu\\.be%2F|%2Fv%2F)[^#&?\\n]*");

    private static final String YOUTUBE_API_URL = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=%s&key=%s";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YoutubeService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    public String getVideoDuration(String videoId) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("YouTube API Key is missing. Cannot fetch video duration.");
            return null;
        }

        try {
            String url = String.format(YOUTUBE_API_URL, videoId, apiKey);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");

            if (items.isArray() && items.size() > 0) {
                String isoDuration = items.get(0).path("contentDetails").path("duration").asText();
                return convertIsoDuration(isoDuration);
            }
        } catch (org.springframework.web.client.RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to fetch YouTube duration for video {}: {}", videoId, e.getMessage());
        }
        return null;
    }

    private String convertIsoDuration(String isoDuration) {
        try {
            Duration duration = Duration.parse(isoDuration);
            long absSeconds = Math.abs(duration.getSeconds());

            String positive = String.format(
                    "%02d:%02d:%02d",
                    absSeconds / 3600,
                    (absSeconds % 3600) / 60,
                    absSeconds % 60);

            if (positive.startsWith("00:")) {
                return positive.substring(3);
            }
            return positive;
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Failed to parse duration: {}", isoDuration);
            return null;
        }
    }

    public String extractVideoId(String url) {
        if (url == null) return null;
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group() : null;
    }

    public boolean isVideoAccessible(String videoId) {
        try {
            String url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId
                    + "&format=json";
            org.springframework.http.ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound
                | org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check video accessibility for {}: {}", videoId, e.getMessage());
            return false;
        }
    }
}
