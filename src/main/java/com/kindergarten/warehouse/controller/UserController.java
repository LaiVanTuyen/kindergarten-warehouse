package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<com.kindergarten.warehouse.dto.response.UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}/block")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<com.kindergarten.warehouse.dto.response.UserResponse> toggleBlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleBlockUser(id));
    }

    @PutMapping("/profile")
    public ResponseEntity<com.kindergarten.warehouse.dto.response.UserResponse> updateProfile(
            Authentication authentication,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String password) {
        String username = authentication.getName();
        return ResponseEntity.ok(userService.updateProfile(username, fullName, password));
    }
}
