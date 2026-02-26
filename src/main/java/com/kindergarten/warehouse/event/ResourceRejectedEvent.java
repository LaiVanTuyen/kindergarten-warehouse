package com.kindergarten.warehouse.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRejectedEvent {
    private String uploaderId;
    private String uploaderEmail;
    private String uploaderName;
    private String documentTitle;
    private String reason;
}
