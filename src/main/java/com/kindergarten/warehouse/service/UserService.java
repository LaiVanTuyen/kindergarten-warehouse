package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> getUsers(String status) {
        if ("DELETED".equalsIgnoreCase(status)) {
            return userRepository.findByIsDeletedTrue(org.springframework.data.domain.Pageable.unpaged())
                    .stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
        } else if (status != null && !status.isEmpty()) {
            try {
                com.kindergarten.warehouse.entity.UserStatus userStatus = com.kindergarten.warehouse.entity.UserStatus
                        .valueOf(status.toUpperCase());
                return userRepository
                        .findByIsDeletedFalseAndStatus(userStatus, org.springframework.data.domain.Pageable.unpaged())
                        .stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Should probably throw bad request, but for now just return active
                return userRepository.findByIsDeletedFalse(org.springframework.data.domain.Pageable.unpaged())
                        .stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
            }
        }
        return userRepository.findByIsDeletedFalse(org.springframework.data.domain.Pageable.unpaged())
                .stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
    }

    public List<UserResponse> getAllUsers() {
        return getUsers(null);
    }

    @com.kindergarten.warehouse.aspect.LogAction(action = "CREATE", description = "Created user")
    public UserResponse createUser(com.kindergarten.warehouse.dto.request.UserCreationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.EMAIL_EXISTED);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(request.getRoles().stream()
                    .map(com.kindergarten.warehouse.entity.Role::valueOf)
                    .collect(java.util.stream.Collectors.toSet()));
        } else {
            user.setRoles(java.util.Collections.singleton(com.kindergarten.warehouse.entity.Role.USER));
        }

        user.setStatus(com.kindergarten.warehouse.entity.UserStatus.ACTIVE);

        return mapToResponse(userRepository.save(user));
    }

    @org.springframework.transaction.annotation.Transactional
    @com.kindergarten.warehouse.aspect.LogAction(action = "DELETE", description = "Deleted user")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new com.kindergarten.warehouse.exception.AppException(
                                com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        if (user.getIsDeleted()) {
            return;
        }

        user.setEmail(user.getEmail() + "_deleted_" + System.currentTimeMillis());
        user.setUsername(user.getUsername() + "_deleted_" + System.currentTimeMillis());
        user.setIsDeleted(true);
        // user.setStatus(com.kindergarten.warehouse.entity.UserStatus.BLOCKED); // Do
        // not change status explicitly
        userRepository.save(user);
    }

    @org.springframework.transaction.annotation.Transactional
    @com.kindergarten.warehouse.aspect.LogAction(action = "UPDATE", description = "Restored user")
    public UserResponse restoreUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new com.kindergarten.warehouse.exception.AppException(
                                com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        if (!user.getIsDeleted()) {
            return mapToResponse(user);
        }

        String originalEmail = user.getEmail().replaceAll("_deleted_\\d+$", "");
        String originalUsername = user.getUsername().replaceAll("_deleted_\\d+$", "");

        if (userRepository.existsByEmail(originalEmail)) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.EMAIL_EXISTED);
        }

        if (userRepository.existsByUsername(originalUsername)) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.USER_EXISTED);
        }

        user.setEmail(originalEmail);
        user.setUsername(originalUsername);
        user.setIsDeleted(false);
        // user.setStatus(com.kindergarten.warehouse.entity.UserStatus.ACTIVE); // Do
        // not change status

        return mapToResponse(userRepository.save(user));
    }

    @com.kindergarten.warehouse.aspect.LogAction(action = "UPDATE", description = "Toggled block status for user")
    public UserResponse toggleBlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));
        user.setStatus(user.getStatus() == com.kindergarten.warehouse.entity.UserStatus.ACTIVE
                ? com.kindergarten.warehouse.entity.UserStatus.BLOCKED
                : com.kindergarten.warehouse.entity.UserStatus.ACTIVE);
        return mapToResponse(userRepository.save(user));
    }

    public UserResponse updateProfile(String username, String fullName,
            String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        if (fullName != null && !fullName.isEmpty()) {
            user.setFullName(fullName);
        }

        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        return mapToResponse(userRepository.save(user));
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                // Map Set<Role> to Set<String>
                .roles(user.getRoles().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet()))
                // Map UserStatus to String
                .status(user.getStatus().name())
                .isDeleted(user.getIsDeleted())
                .build();
    }
}
