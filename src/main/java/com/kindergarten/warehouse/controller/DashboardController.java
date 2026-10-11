package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;
    @GetMapping
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(defaultValue = "this_year") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.success(service.overview(period, startDate, endDate), "Dashboard retrieved successfully");
    }
}
