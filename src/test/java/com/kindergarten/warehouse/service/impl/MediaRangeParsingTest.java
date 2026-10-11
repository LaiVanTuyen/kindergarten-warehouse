package com.kindergarten.warehouse.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RFC 9110 single-range parsing used by GET /resources/{id}/stream. */
class MediaRangeParsingTest {

    private static long[] parse(String header) {
        return ResourceServiceImpl.parseSingleRange(header, 1000);
    }

    @Test
    void boundedOpenAndSuffixRanges() {
        assertThat(parse("bytes=0-99")).containsExactly(0, 99);
        assertThat(parse("bytes=900-")).containsExactly(900, 999);
        assertThat(parse("bytes=-100")).containsExactly(900, 999);
        assertThat(parse("bytes=950-5000")).containsExactly(950, 999);   // end clamped
        assertThat(parse("bytes=-5000")).containsExactly(0, 999);        // suffix larger than file
    }

    @Test
    void unsatisfiableRangesAreEmpty() {
        assertThat(parse("bytes=1000-")).isEmpty();
        assertThat(parse("bytes=500-100")).isEmpty();
        assertThat(parse("bytes=-0")).isEmpty();
    }

    @Test
    void absentMalformedOrMultiRangeFallsBackToWholeBody() {
        assertThat(parse(null)).isNull();
        assertThat(parse("items=0-1")).isNull();
        assertThat(parse("bytes=abc-")).isNull();
        assertThat(parse("bytes=0-1,5-6")).isNull();
        assertThat(parse("bytes=-")).isNull();
    }
}
