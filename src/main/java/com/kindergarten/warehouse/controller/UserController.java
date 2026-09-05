package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.AdminUpdateUserRequest;
import com.kindergarten.warehouse.dto.request.BlockUserRequest;
import com.kindergarten.warehouse.dto.request.ChangePasswordRequest;
import com.kindergarten.warehouse.dto.request.ConfirmResetPasswordRequest;
import com.kindergarten.warehouse.dto.request.UpdateProfileRequest;
import com.kindergarten.warehouse.dto.request.UserCreationRequest;
import com.kindergarten.warehouse.dto.request.UserFilterRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.UserService;
import com.kindergarten.warehouse.util.PageableUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "username", "email", "fullName", "status", "lastActive", "createdAt", "updatedAt");

    private final UserService userService;
    private final MessageService messageService;

    // =================================================== SELF-SERVICE (USER)

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getCurrentUser(authentication),
                messageService.getMessage("user.me.success")));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(Authentication authentication,
                                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateProfile(authentication, request),
                messageService.getMessage("user.profile.update.success")));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication authentication,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication, request);
        return ResponseEntity.ok(ApiResponse.success(null,
                messageService.getMessage("user.password.change.success")));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(Authentication authentication,
                                                                  @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateAvatar(authentication, file),
                messageService.getMessage("user.profile.update.success")));
    }

    // ============================================================ ADMIN OPS

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                userService.createUser(request),
                messageService.getMessage("user.create.success")));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @ModelAttribute UserFilterRequest filterRequest,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable requestedPageable) {

        Pageable pageable = PageableUtils.sanitize(
                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUsers(filterRequest, pageable),
                messageService.getMessage("user.list.success")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable Long id,
                                                                @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUser(id, request),
                messageService.getMessage("user.update.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(
                Collections.singletonMap("id", id),
                messageService.getMessage("user.delete.success")));
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> restoreUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.restoreUser(id),
                messageService.getMessage("user.restore.success")));
    }

    @PatchMapping("/{id}/block")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> toggleBlockUser(@PathVariable Long id,
                                                                     @Valid @RequestBody(required = false) BlockUserRequest request) {
        UpdateResult<UserResponse> updateResult = userService.toggleBlockUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                updateResult.getResult(),
                messageService.getMessage(updateResult.getMessageKey())));
    }

    @PostMapping("/{id}/reset-password/init")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> initPasswordReset(@PathVariable Long id) {
        userService.initPasswordReset(id);
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("auth.otp.sent")));
    }

    @PostMapping("/{id}/reset-password/confirm")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@PathVariable Long id,
                                                                  @Valid @RequestBody ConfirmResetPasswordRequest request) {
        userService.confirmPasswordReset(id, request.getOtp());
        return ResponseEntity.ok(ApiResponse.success(null,
                messageService.getMessage("auth.password.reset.success")));
    }
}
