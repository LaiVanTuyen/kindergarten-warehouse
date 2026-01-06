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
    private final org.springframework.cache.CacheManager cacheManager;
    private final MinioStorageService minioStorageService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            org.springframework.cache.CacheManager cacheManager, MinioStorageService minioStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cacheManager = cacheManager;
        this.minioStorageService = minioStorageService;
    }

    private void clearUserCache(User user) {
        org.springframework.cache.Cache cache = cacheManager.getCache("users");
        if (cache != null) {
            cache.evict(user.getUsername());
            cache.evict(user.getEmail());
        }
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
        clearUserCache(user);
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

        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
        return mapToResponse(savedUser);
    }

    @com.kindergarten.warehouse.aspect.LogAction(action = "UPDATE", description = "Toggled block status for user")
    public UserResponse toggleBlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));
        user.setStatus(user.getStatus() == com.kindergarten.warehouse.entity.UserStatus.ACTIVE
                ? com.kindergarten.warehouse.entity.UserStatus.BLOCKED
                : com.kindergarten.warehouse.entity.UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
        return mapToResponse(savedUser);
    }

    @com.kindergarten.warehouse.aspect.LogAction(action = "UPDATE", description = "Updated profile info")
    public UserResponse updateProfile(String usernameOrEmail,
            com.kindergarten.warehouse.dto.request.UpdateProfileRequest request) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        if (request.getFullName() != null && !request.getFullName().isEmpty()) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
        return mapToResponse(savedUser);
    }

    @com.kindergarten.warehouse.aspect.LogAction(action = "UPDATE", description = "Changed password")
    public void changePassword(String usernameOrEmail,
            com.kindergarten.warehouse.dto.request.ChangePasswordRequest request) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.INVALID_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
    }

    @com.kindergarten.warehouse.aspect.LogAction(action = "UPDATE", description = "Updated avatar")
    public UserResponse updateAvatar(String usernameOrEmail, org.springframework.web.multipart.MultipartFile file) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed for avatar");
        }

        String oldAvatarUrl = user.getAvatarUrl();
        String avatarUrl = minioStorageService.uploadFile(file, "avatars");

        user.setAvatarUrl(avatarUrl);
        User savedUser = userRepository.save(user);

        // Clean up old avatar to save space
        if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty()) {
            try {
                minioStorageService.deleteFile(oldAvatarUrl);
            } catch (Exception e) {
                // Silently fail or log warning so user experience isn't affected
                // In production, effective logging should be used here
            }
        }

        clearUserCache(savedUser);
        return mapToResponse(savedUser);
    }

    private UserResponse mapToResponse(User user) {
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            // Generate presigned URL for secure access
            String key = extractKeyFromUrl(avatarUrl);
            try {
                avatarUrl = minioStorageService.getPresignedUrl(key);
            } catch (Exception e) {
                // Fallback to original URL if signing fails, though it might still be 403
            }
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(avatarUrl)
                // Map Set<Role> to Set<String>
                .roles(user.getRoles().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet()))
                // Map UserStatus to String
                .status(user.getStatus().name())
                .isDeleted(user.getIsDeleted())
                .phoneNumber(user.getPhoneNumber())
                .createdAt(user.getCreatedAt())
                .bio(user.getBio())
                .build();
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null)
            return null;
        if (fileUrl.contains("/avatars/")) {
            return fileUrl.substring(fileUrl.indexOf("avatars/"));
        }
        if (fileUrl.lastIndexOf("/") != -1) {
            return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        }
        return fileUrl;
    }
}
