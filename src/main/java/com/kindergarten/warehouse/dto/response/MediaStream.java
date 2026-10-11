package com.kindergarten.warehouse.dto.response;

import java.io.InputStream;

/**
 * A (possibly partial) media body for {@code GET /resources/{id}/stream}.
 *
 * @param body        null when {@code unsatisfiable}
 * @param partial     true → 206 with {@code Content-Range: bytes start-end/total}
 * @param unsatisfiable true → 416 with {@code Content-Range: bytes * /total}
 */
public record MediaStream(
        InputStream body,
        String contentType,
        long total,
        long start,
        long end,
        boolean partial,
        boolean unsatisfiable) {

    public long length() {
        return end - start + 1;
    }
}
