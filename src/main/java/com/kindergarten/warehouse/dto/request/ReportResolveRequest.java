package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param action       RESOLVE (report is valid) or DISMISS (not a violation)
 * @param note         shown to the reporter in their notification
 * @param hideResource only with RESOLVE: take the resource down (status REJECTED with the
 *                     violation as reason). The owner cannot undo this by changing
 *                     visibility; only an edit (→ PENDING) plus a new approval brings it back.
 */
public record ReportResolveRequest(
        @NotNull Action action,
        @Size(max = 1000) String note,
        boolean hideResource) {

    public enum Action {
        RESOLVE,
        DISMISS
    }
}
