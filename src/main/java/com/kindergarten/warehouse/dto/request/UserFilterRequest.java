package com.kindergarten.warehouse.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class UserFilterRequest {
    @Setter
    private String keyword;

    // Multi-select filters
    private List<String> statuses;
    private List<String> roles;

    // --- Custom Setters to handle comma-separated values ---

    public List<String> getStatuses() {
        return statuses;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setStatuses(List<String> statuses) {
        this.statuses = splitCommaSeparatedList(statuses);
    }

    public void setRoles(List<String> roles) {
        this.roles = splitCommaSeparatedList(roles);
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
