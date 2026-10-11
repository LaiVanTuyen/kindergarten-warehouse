package com.kindergarten.warehouse.dto.response;

/** No storage URL or original document is exposed by this contract. */
public record PreviewResponse(String status, int pageCount, int totalPages,
                              boolean truncated, String message) {
}
