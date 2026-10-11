package com.kindergarten.warehouse.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normalizes free-text tags: trimmed, single-spaced, lower-case, max 50 chars, max 10 per resource. */
public final class TagUtils {

    public static final int MAX_TAG_LENGTH = 50;
    public static final int MAX_TAGS_PER_RESOURCE = 10;

    private TagUtils() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String tag = raw.trim().replaceFirst("^#+", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH) {
            return null;
        }
        return tag;
    }

    /** Accepts both repeated params and a single comma-separated value. */
    public static List<String> normalizeAll(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        // Keyed by the accent-folded form: resource_tags.tag uses utf8mb4_0900_ai_ci, so
        // MySQL treats "bà" and "ba" as the same PK value. Keeping both would fail the
        // INSERT with 1062 and roll back the whole upload. First spelling wins.
        Map<String, String> tagsByKey = new LinkedHashMap<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String tag = normalize(part);
                if (tag != null) {
                    tagsByKey.putIfAbsent(foldKey(tag), tag);
                }
            }
        }
        return new ArrayList<>(tagsByKey.values());
    }

    /**
     * Approximates utf8mb4_0900_ai_ci equality for tags: case- and accent-insensitive,
     * with "đ" = "d" (a separate letter that NFD does not decompose).
     */
    static String foldKey(String tag) {
        String decomposed = Normalizer.normalize(tag.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd');
    }

    /** Normalized tags for storing on a resource, capped at {@link #MAX_TAGS_PER_RESOURCE}. */
    public static Set<String> forResource(List<String> raw) {
        List<String> tags = normalizeAll(raw);
        return new LinkedHashSet<>(tags.subList(0, Math.min(tags.size(), MAX_TAGS_PER_RESOURCE)));
    }
}
