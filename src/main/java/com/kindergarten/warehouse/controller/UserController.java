package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.UserResponse;

import com.kindergarten.warehouse.service.MessageService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final MessageService messageService;

    public UserController(UserService userService, MessageService messageService) {
        this.userService = userService;
        this.messageService = messageService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity
                .ok(ApiResponse.success(userService.getAllUsers(), messageService.getMessage("user.list.success")));
    }

    @PutMapping("/{id}/block")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> toggleBlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.toggleBlockUser(id), messageService.getMessage("user.block.success")));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            Authentication authentication,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String password) {
        String username = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(username, fullName, password),
                messageService.getMessage("user.profile.update.success")));
    }
}
