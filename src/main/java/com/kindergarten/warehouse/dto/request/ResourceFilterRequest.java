package com.kindergarten.warehouse.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ResourceFilterRequest {
    @Setter
    private String keyword;
    
    // ID filters (Legacy/Internal use)
    @Setter
    private Long topicId;
    @Setter
    private Long categoryId;
    @Setter
    private Long ageGroupId;
    
    // Slug filters (Public/SEO friendly - Multi-select supported)
    private List<String> topicSlugs;
    private List<String> categorySlugs;
    private List<String> ageSlugs;
    
    // Resource Type (e.g., VIDEO, AUDIO, DOCUMENT - Multi-select supported)
    private List<String> types;

    @Setter
    private String status;
    @Setter
    private Long createdBy; // Filter by creator ID

    // --- Custom Setters to handle comma-separated values (e.g., ?types=VIDEO,PDF) ---

    public void setTopicSlugs(List<String> topicSlugs) {
        this.topicSlugs = splitCommaSeparatedList(topicSlugs);
    }

    public void setCategorySlugs(List<String> categorySlugs) {
        this.categorySlugs = splitCommaSeparatedList(categorySlugs);
    }

    public void setAgeSlugs(List<String> ageSlugs) {
        this.ageSlugs = splitCommaSeparatedList(ageSlugs);
    }

    public void setTypes(List<String> types) {
        this.types = splitCommaSeparatedList(types);
    }

    /**
     * Helper method to split strings containing commas into separate list elements.
     * Solves the issue where ?param=A,B is treated as ["A,B"] instead of ["A", "B"]
     */
    private List<String> splitCommaSeparatedList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // If list has 1 element and contains comma, split it
        if (input.size() == 1 && input.get(0).contains(",")) {
            return Arrays.stream(input.get(0).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        return input;
    }
}
