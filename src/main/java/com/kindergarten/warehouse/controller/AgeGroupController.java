package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.service.AgeGroupService;
import com.kindergarten.warehouse.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import com.kindergarten.warehouse.util.PageableUtils;

@RestController
@RequestMapping("/api/v1/age-groups")
@RequiredArgsConstructor
public class AgeGroupController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "name", "slug", "minAge", "maxAge", "createdAt", "updatedAt");

    private final AgeGroupService ageGroupService;
    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AgeGroupResponse>>> getAllAgeGroups(
            @PageableDefault(size = 10, sort = "minAge", direction = Sort.Direction.ASC) Pageable requestedPageable) {
        Pageable pageable = PageableUtils.sanitize(
                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.ASC, "minAge"));
        return ResponseEntity.ok(ApiResponse.success(
                ageGroupService.getAllAgeGroups(pageable),
                messageService.getMessage("agegroup.list.success")));
    }
}
