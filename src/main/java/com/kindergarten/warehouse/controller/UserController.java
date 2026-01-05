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

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @jakarta.validation.Valid @RequestBody com.kindergarten.warehouse.dto.request.UserCreationRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.createUser(request), messageService.getMessage("user.create.success")));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestParam(required = false) String status) {
        return ResponseEntity
                .ok(ApiResponse.success(userService.getUsers(status), messageService.getMessage("user.list.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(java.util.Collections.singletonMap("id", id),
                messageService.getMessage("user.delete.success")));
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> restoreUser(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.restoreUser(id), messageService.getMessage("user.restore.success")));
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
            @RequestBody com.kindergarten.warehouse.dto.request.UpdateProfileRequest request) {
        String usernameOrEmail = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(usernameOrEmail, request),
                messageService.getMessage("user.profile.update.success")));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody com.kindergarten.warehouse.dto.request.ChangePasswordRequest request) {
        String usernameOrEmail = authentication.getName();
        userService.changePassword(usernameOrEmail, request);
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("user.password.change.success")));
    }
}
