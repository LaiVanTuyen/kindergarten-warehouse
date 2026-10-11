package com.kindergarten.warehouse.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TagUtilsTest {

    @Test
    void normalizesCaseSpacingAndHashPrefix() {
        assertThat(TagUtils.normalize("  #Toán   Học ")).isEqualTo("toán học");
    }

    @Test
    void rejectsBlankAndTooLongTags() {
        assertThat(TagUtils.normalize("   ")).isNull();
        assertThat(TagUtils.normalize("a".repeat(51))).isNull();
        assertThat(TagUtils.normalize(null)).isNull();
    }

    @Test
    void splitsCommaSeparatedValuesAndDeduplicates() {
        assertThat(TagUtils.normalizeAll(List.of("Âm nhạc, mầm non", "âm nhạc")))
                .containsExactly("âm nhạc", "mầm non");
        assertThat(TagUtils.normalizeAll(Arrays.asList(null, ""))).isEmpty();
    }

    @Test
    void deduplicatesAccentVariantsLikeMySqlCollation() {
        // resource_tags uses utf8mb4_0900_ai_ci: "bà" = "ba", "đ" = "d". Keeping both fails with 1062.
        assertThat(TagUtils.normalizeAll(List.of("bà, ba, BÀ, đồ chơi, do choi")))
                .containsExactly("bà", "đồ chơi");
    }

    @Test
    void capsTagsPerResource() {
        List<String> many = IntStream.range(0, 15).mapToObj(i -> "tag" + i).toList();
        assertThat(TagUtils.forResource(many)).hasSize(TagUtils.MAX_TAGS_PER_RESOURCE).contains("tag0", "tag9");
    }
}
