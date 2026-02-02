package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.AdminUpdateUserRequest;
import com.kindergarten.warehouse.dto.request.ChangePasswordRequest;
import com.kindergarten.warehouse.dto.request.UpdateProfileRequest;
import com.kindergarten.warehouse.dto.request.UserCreationRequest;
import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.UserMapper;
import com.kindergarten.warehouse.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;
    private final MinioStorageService minioStorageService;
    private final RedisOtpService redisOtpService;
    private final EmailService emailService;
    private final UserMapper userMapper;

    private void clearUserCache(User user) {
        Cache cache = cacheManager.getCache("users");
        if (cache != null) {
            cache.evict(user.getUsername());
            cache.evict(user.getEmail());
        }
    }

    public Page<UserResponse> getUsers(String status, String role, String keyword, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Role Filter
            if (role != null && !role.isEmpty()) {
                try {
                    Role roleEnum = Role.valueOf(role.toUpperCase());
                    predicates.add(cb.isMember(roleEnum, root.get("roles")));
                } catch (IllegalArgumentException e) {
                    // Ignore invalid role
                }
            }

            // Status Filter
            if ("DELETED".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
                if (status != null && !status.isEmpty()) {
                    try {
                        UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
                        predicates.add(cb.equal(root.get("status"), userStatus));
                    } catch (IllegalArgumentException e) {
                        // Ignore invalid status
                    }
                }
            }

            // Keyword Search
            if (keyword != null && !keyword.isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), likePattern),
                        cb.like(cb.lower(root.get("email")), likePattern),
                        cb.like(cb.lower(root.get("fullName")), likePattern),
                        cb.like(cb.lower(root.get("phoneNumber")), likePattern)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable).map(userMapper::toResponse);
    }

    @LogAction(action = "CREATE", description = "Created user")
    public UserResponse createUser(UserCreationRequest request) {
        checkUsernameAndEmailAvailability(request.getUsername(), request.getEmail());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(request.getRoles().stream()
                    .map(Role::valueOf)
                    .collect(Collectors.toSet()));
        } else {
            user.setRoles(Collections.singleton(Role.USER));
        }

        user.setStatus(UserStatus.ACTIVE);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    @LogAction(action = "DELETE", description = "Deleted user")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getIsDeleted()) {
            return;
        }

        user.setEmail(user.getEmail() + "_deleted_" + System.currentTimeMillis());
        user.setUsername(user.getUsername() + "_deleted_" + System.currentTimeMillis());
        user.setIsDeleted(true);
        // user.setStatus(UserStatus.BLOCKED); // Do not change status explicitly
        userRepository.save(user);
        clearUserCache(user);
    }

    @Transactional
    @LogAction(action = "UPDATE", description = "Restored user")
    public UserResponse restoreUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsDeleted()) {
            return userMapper.toResponse(user);
        }

        String originalEmail = user.getEmail().replaceAll("_deleted_\\d+$", "");
        String originalUsername = user.getUsername().replaceAll("_deleted_\\d+$", "");

        checkUsernameAndEmailAvailability(originalUsername, originalEmail);

        user.setEmail(originalEmail);
        user.setUsername(originalUsername);
        user.setIsDeleted(false);
        // user.setStatus(UserStatus.ACTIVE); // Do not change status

        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
        return userMapper.toResponse(savedUser);
    }

    @LogAction(action = "UPDATE", description = "Toggled block status for user")
    public UpdateResult<UserResponse> toggleBlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isBlocking = user.getStatus() == UserStatus.ACTIVE;
        user.setStatus(isBlocking ? UserStatus.BLOCKED : UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);

        String messageKey = isBlocking ? "user.blocked" : "user.unblocked";
        return new UpdateResult<>(userMapper.toResponse(savedUser), messageKey);
    }

    @LogAction(action = "UPDATE", description = "Admin updated user details")
    public UserResponse updateUser(Long id, AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(request.getRoles().stream()
                    .map(Role::valueOf)
                    .collect(Collectors.toSet()));
        }

        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
        return userMapper.toResponse(savedUser);
    }

    @LogAction(action = "UPDATE", description = "Admin initiated password reset")
    public void initPasswordReset(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String otp = redisOtpService.generateOtp(id);

        // Asynchronously send email
        emailService.sendOtp(user.getEmail(), otp);
    }

    @LogAction(action = "UPDATE", description = "Admin confirmed password reset")
    public void confirmPasswordReset(Long id, String otp) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!redisOtpService.validateOtp(id, otp)) {
            // Throw exception for Invalid OTP
            // We might need a specific ErrorCode for this, but for now reuse or create
            // generic
            throw new IllegalArgumentException("Invalid or Expired OTP");
        }

        String newPassword = generateStrongPassword();
        user.setPassword(passwordEncoder.encode(newPassword));

        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);

        // Send new password to user
        emailService.sendNewPassword(user.getEmail(), newPassword);
    }

    @LogAction(action = "UPDATE", description = "Updated profile info")
    public UserResponse updateProfile(String usernameOrEmail, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

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
        return userMapper.toResponse(savedUser);
    }

    @LogAction(action = "UPDATE", description = "Changed password")
    public void changePassword(String usernameOrEmail, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        User savedUser = userRepository.save(user);
        clearUserCache(savedUser);
    }

    @LogAction(action = "UPDATE", description = "Updated avatar")
    public UserResponse updateAvatar(String usernameOrEmail, MultipartFile file) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String contentType = file.getContentType();
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
            }
        }

        clearUserCache(savedUser);
        return userMapper.toResponse(savedUser);
    }

    private void checkUsernameAndEmailAvailability(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
    }

    private String generateStrongPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "@$!%*?&";
        String all = upper + lower + digits + special;

        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder password = new StringBuilder();

        // Ensure at least one of each required type
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Fill remaining 4 chars (to make total 8)
        for (int i = 0; i < 4; i++) {
            password.append(all.charAt(random.nextInt(all.length())));
        }

        // Shuffle to avoid predictable pattern
        List<String> chars = java.util.Arrays.asList(password.toString().split(""));
        Collections.shuffle(chars);
        return String.join("", chars);
    }
}
