package com.kindergarten.warehouse.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class AuditLogFilterRequest {
    @Setter
    private String username;

    @Setter
    private String startDate; // yyyy-MM-dd
    @Setter
    private String endDate; // yyyy-MM-dd

    // Multi-select filters
    private List<String> action;
    private List<String> target;

    // --- Custom Setters ---

    public void setAction(List<String> action) {
        this.action = splitCommaSeparatedList(action);
    }

    public void setTarget(List<String> target) {
        this.target = splitCommaSeparatedList(target);
    }

    private List<String> splitCommaSeparatedList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (input.size() == 1 && input.get(0).contains(",")) {
            return Arrays.stream(input.get(0).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        return input;
    }
}
