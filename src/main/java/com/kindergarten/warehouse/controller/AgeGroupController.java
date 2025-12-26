package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.service.AgeGroupService;
import com.kindergarten.warehouse.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/age-groups")
@RequiredArgsConstructor
public class AgeGroupController {

    private final AgeGroupService ageGroupService;
    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgeGroupResponse>>> getAllAgeGroups() {
        return ResponseEntity.ok(ApiResponse.success(
                ageGroupService.getAllAgeGroups(),
                messageService.getMessage("agegroup.list.success") // Make sure to add this message key if needed or use
                                                                   // a generic one
        ));
    }
}
