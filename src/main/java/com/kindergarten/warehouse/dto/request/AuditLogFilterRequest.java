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
    private String endDate;   // yyyy-MM-dd

    // Multi-select filters
    private List<String> actions;
    private List<String> targets;

    // --- Custom Setters ---

    public void setActions(List<String> actions) {
        this.actions = splitCommaSeparatedList(actions);
    }

    public void setTargets(List<String> targets) {
        this.targets = splitCommaSeparatedList(targets);
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
