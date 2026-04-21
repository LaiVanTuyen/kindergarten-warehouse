package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.AdminUpdateUserRequest;
import com.kindergarten.warehouse.dto.request.BlockUserRequest;
import com.kindergarten.warehouse.dto.request.ChangePasswordRequest;
import com.kindergarten.warehouse.dto.request.UpdateProfileRequest;
import com.kindergarten.warehouse.dto.request.UserCreationRequest;
import com.kindergarten.warehouse.dto.request.UserFilterRequest;
import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.entity.AuditAction;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.UserMapper;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.CustomUserDetails;
import com.kindergarten.warehouse.util.AppConstants;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quản lý tài khoản user: CRUD, profile, ảnh đại diện, đổi/đặt lại mật khẩu.
 *
 * <p>Quy tắc nghiệp vụ bắt buộc:
 * <ul>
 *   <li>Admin không thể thao tác xóa/khóa/hạ quyền chính mình.</li>
 *   <li>Không thể xóa / khóa / hạ quyền admin nếu đó là admin active cuối cùng.</li>
 *   <li>Khi đổi mật khẩu, block, hoặc reset, {@code tokenVersion} tăng để vô hiệu hóa JWT đã cấp.</li>
 *   <li>Email (notify block/unblock) gửi sau khi commit transaction thành công.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int GENERATED_PASSWORD_LENGTH = 12;
    private static final String DELETE_SUFFIX_PATTERN = "_deleted_\\d+$";
    private static final String[] ALLOWED_AVATAR_MIME = {"image/jpeg", "image/png", "image/webp"};

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;
    private final MinioStorageService minioStorageService;
    private final RedisOtpService redisOtpService;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // =============================================================== QUERIES

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(UserFilterRequest filterRequest, Pageable pageable) {
        Specification<User> spec = buildUserSpecification(filterRequest);
        return userRepository.findAll(spec, pageable).map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        Long userId = requireCurrentUserId(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    // ============================================================= ADMIN OPS

    @Transactional
    @LogAction(action = AuditAction.CREATE, description = "Created user", target = "USER")
    public UserResponse createUser(UserCreationRequest request) {
        ensureUsernameAndEmailAvailable(request.getUsername(), request.getEmail());

        Set<Role> roles = parseRolesOrDefault(request.getRoles());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(roles)
                .status(UserStatus.ACTIVE)
                // Admin tạo trực tiếp → mặc định trusted, bỏ qua verify email
                .emailVerified(true)
                .isDeleted(false)
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    @LogAction(action = AuditAction.DELETE, description = "Deleted user", target = "USER")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            return;
        }

        guardNotSelf(user);
        guardNotLastActiveAdmin(user);

        long timestamp = System.currentTimeMillis();
        String suffix = "_deleted_" + timestamp;

        user.setOriginalUsername(user.getUsername());
        user.setOriginalEmail(user.getEmail());
        user.setUsername(truncateWithSuffix(user.getUsername(), suffix, 50));
        user.setEmail(truncateWithSuffix(user.getEmail(), suffix, 100));
        user.setIsDeleted(true);
        user.incrementTokenVersion(); // vô hiệu hóa mọi JWT đang hoạt động

        User saved = userRepository.save(user);
        evictUserCache(saved);
    }

    @Transactional
    @LogAction(action = AuditAction.RESTORE, description = "Restored user", target = "USER")
    public UserResponse restoreUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsDeleted())) {
            return userMapper.toResponse(user);
        }

        String restoredUsername = user.getOriginalUsername() != null
                ? user.getOriginalUsername()
                : user.getUsername().replaceAll(DELETE_SUFFIX_PATTERN, "");
        String restoredEmail = user.getOriginalEmail() != null
                ? user.getOriginalEmail()
                : user.getEmail().replaceAll(DELETE_SUFFIX_PATTERN, "");

        ensureUsernameAndEmailAvailable(restoredUsername, restoredEmail);

        user.setUsername(restoredUsername);
        user.setEmail(restoredEmail);
        user.setOriginalUsername(null);
        user.setOriginalEmail(null);
        user.setIsDeleted(false);

        User saved = userRepository.save(user);
        evictUserCache(saved);
        return userMapper.toResponse(saved);
    }

    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Toggled block status", target = "USER_TOGGLE_BLOCK")
    public UpdateResult<UserResponse> toggleBlockUser(Long userId, BlockUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        guardNotSelf(user);

        boolean willBlock = user.getStatus() == UserStatus.ACTIVE;
        if (willBlock) {
            guardNotLastActiveAdmin(user);
            user.setStatus(UserStatus.BLOCKED);
            user.setBlockedReason(request == null ? null : request.getReason());
            user.setBlockedAt(LocalDateTime.now());
            user.incrementTokenVersion();
        } else {
            user.setStatus(UserStatus.ACTIVE);
            user.setBlockedReason(null);
            user.setBlockedAt(null);
        }

        User saved = userRepository.save(user);
        evictUserCache(saved);

        final String recipientEmail = saved.getEmail();
        final String blockReason = saved.getBlockedReason();
        runAfterCommit(() -> {
            if (willBlock) {
                emailService.sendAccountBlockedNotification(recipientEmail, blockReason);
            } else {
                emailService.sendAccountUnblockedNotification(recipientEmail);
            }
        });

        String messageKey = willBlock ? "user.blocked" : "user.unblocked";
        return new UpdateResult<>(userMapper.toResponse(saved), messageKey);
    }

    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Admin updated user details", target = "USER")
    public UserResponse updateUser(Long id, AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getStatus() != null) user.setStatus(request.getStatus());

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> newRoles = parseRolesOrDefault(request.getRoles());
            // Nếu đang hạ admin cuối cùng thì chặn
            if (user.hasRole(Role.ADMIN) && !newRoles.contains(Role.ADMIN)) {
                guardNotLastActiveAdmin(user);
            }
            user.setRoles(newRoles);
            user.incrementTokenVersion(); // role thay đổi → JWT cũ phải được reload
        }

        User saved = userRepository.save(user);
        evictUserCache(saved);
        return userMapper.toResponse(saved);
    }

    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Admin initiated password reset", target = "USER_PASSWORD_RESET")
    public void initPasswordReset(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String otp = redisOtpService.generateOtp(RedisOtpService.Purpose.ADMIN_PASSWORD_RESET, String.valueOf(id));
        final String email = user.getEmail();
        runAfterCommit(() -> emailService.sendOtpForPasswordReset(email, otp));
    }

    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Admin confirmed password reset", target = "USER_PASSWORD_RESET")
    public void confirmPasswordReset(Long id, String otp) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        redisOtpService.verifyOtp(RedisOtpService.Purpose.ADMIN_PASSWORD_RESET, String.valueOf(id), otp);

        String newPassword = generateStrongPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.incrementTokenVersion();

        User saved = userRepository.save(user);
        evictUserCache(saved);

        final String email = saved.getEmail();
        runAfterCommit(() -> emailService.sendNewPassword(email, newPassword));
        // NOTE: Flow này (admin reset → gửi password mới qua email) là flow cũ để tương thích.
        // Khuyến nghị dùng /auth/forgot-password do user chủ động.
    }

    // ======================================================== SELF-SERVICE

    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Updated profile info", target = "USER_PROFILE")
    public UserResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = loadCurrentUser(authentication);

        if (request.getFullName() != null && !request.getFullName().isEmpty()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        User saved = userRepository.save(user);
        evictUserCache(saved);
        return userMapper.toResponse(saved);
    }

    @Transactional
    @LogAction(action = AuditAction.UPDATE, description = "Changed password", target = "USER_PASSWORD_CHANGE")
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        User user = loadCurrentUser(authentication);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.incrementTokenVersion();

        User saved = userRepository.save(user);
        evictUserCache(saved);
    }

    @Transactional
    @LogAction(action = AuditAction.UPLOAD, description = "Updated avatar", target = "USER_AVATAR")
    public UserResponse updateAvatar(Authentication authentication, MultipartFile file) {
        User user = loadCurrentUser(authentication);
        validateAvatar(file);

        String oldAvatarUrl = user.getAvatarUrl();
        String avatarUrl = minioStorageService.uploadFile(file, AppConstants.FOLDER_AVATARS);

        user.setAvatarUrl(avatarUrl);
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (RuntimeException persistError) {
            // Nếu DB ghi fail → xóa file mới để không để lại rác trên MinIO
            safeDeleteFile(avatarUrl);
            throw persistError;
        }

        if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty()) {
            runAfterCommit(() -> safeDeleteFile(oldAvatarUrl));
        }

        evictUserCache(saved);
        return userMapper.toResponse(saved);
    }

    // ============================================================ INTERNAL

    private Specification<User> buildUserSpecification(UserFilterRequest filterRequest) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            List<Role> roles = parseRoleStrings(filterRequest.getRoles());
            if (!roles.isEmpty()) {
                predicates.add(root.join("roles").in(roles));
                if (query != null) query.distinct(true);
            }

            List<String> rawStatuses = filterRequest.getStatuses();
            boolean includeDeleted = rawStatuses != null && rawStatuses.stream().anyMatch(s -> "DELETED".equalsIgnoreCase(s));

            List<UserStatus> statuses = rawStatuses == null ? List.of()
                    : rawStatuses.stream()
                            .filter(s -> !"DELETED".equalsIgnoreCase(s))
                            .map(UserService::safeParseStatus)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

            if (includeDeleted) {
                Predicate isDeleted = cb.isTrue(root.get("isDeleted"));
                if (!statuses.isEmpty()) {
                    Predicate activeWithStatus = cb.and(
                            cb.isFalse(root.get("isDeleted")),
                            root.get("status").in(statuses));
                    predicates.add(cb.or(isDeleted, activeWithStatus));
                } else {
                    predicates.add(isDeleted);
                }
            } else {
                predicates.add(cb.isFalse(root.get("isDeleted")));
                if (!statuses.isEmpty()) {
                    predicates.add(root.get("status").in(statuses));
                }
            }

            if (filterRequest.getKeyword() != null && !filterRequest.getKeyword().isEmpty()) {
                String like = "%" + filterRequest.getKeyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("phoneNumber")), like)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static UserStatus safeParseStatus(String s) {
        try {
            return UserStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Role> parseRoleStrings(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream()
                .map(s -> {
                    try {
                        return Role.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static Set<Role> parseRolesOrDefault(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.singleton(Role.USER);
        }
        try {
            return raw.stream().map(s -> Role.valueOf(s.toUpperCase())).collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_ROLE);
        }
    }

    private void ensureUsernameAndEmailAvailable(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
    }

    private User loadCurrentUser(Authentication authentication) {
        Long id = requireCurrentUserId(authentication);
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Long requireCurrentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails cud)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return cud.getId();
    }

    private void guardNotSelf(User target) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud
                && Objects.equals(cud.getId(), target.getId())) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_SELF);
        }
    }

    private void guardNotLastActiveAdmin(User target) {
        if (!target.hasRole(Role.ADMIN)) return;
        long activeAdmins = userRepository.countActiveUsersByRole(Role.ADMIN);
        if (activeAdmins <= 1) {
            throw new AppException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
    }

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_UPLOAD_ERROR);
        }
        if (file.getSize() > AppConstants.AVATAR_MAX_BYTES) {
            throw new AppException(ErrorCode.AVATAR_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new AppException(ErrorCode.FILE_TYPE_INVALID);
        }
        for (String allowed : ALLOWED_AVATAR_MIME) {
            if (allowed.equalsIgnoreCase(contentType)) return;
        }
        throw new AppException(ErrorCode.FILE_TYPE_INVALID);
    }

    private void safeDeleteFile(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            minioStorageService.deleteFile(url);
        } catch (Exception e) {
            log.warn("Failed to delete file from storage: {}", e.getMessage());
        }
    }

    private void evictUserCache(User user) {
        Cache cache = cacheManager.getCache("users");
        if (cache != null) {
            cache.evict(user.getUsername());
            cache.evict(user.getEmail());
            if (user.getOriginalUsername() != null) cache.evict(user.getOriginalUsername());
            if (user.getOriginalEmail() != null) cache.evict(user.getOriginalEmail());
        }
        redisTemplate.delete(AppConstants.REDIS_TOKEN_VERSION_PREFIX + user.getId());
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private static String truncateWithSuffix(String original, String suffix, int maxLength) {
        String candidate = original + suffix;
        if (candidate.length() <= maxLength) return candidate;
        int keep = maxLength - suffix.length();
        if (keep <= 0) {
            return suffix.substring(0, maxLength);
        }
        return original.substring(0, keep) + suffix;
    }

    private static String generateStrongPassword() {
        final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String lower = "abcdefghijklmnopqrstuvwxyz";
        final String digits = "0123456789";
        final String special = "@$!%*?&";
        final String all = upper + lower + digits + special;

        SecureRandom random = new SecureRandom();
        char[] chars = new char[GENERATED_PASSWORD_LENGTH];
        chars[0] = upper.charAt(random.nextInt(upper.length()));
        chars[1] = lower.charAt(random.nextInt(lower.length()));
        chars[2] = digits.charAt(random.nextInt(digits.length()));
        chars[3] = special.charAt(random.nextInt(special.length()));
        for (int i = 4; i < GENERATED_PASSWORD_LENGTH; i++) {
            chars[i] = all.charAt(random.nextInt(all.length()));
        }
        // Shuffle
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char t = chars[i]; chars[i] = chars[j]; chars[j] = t;
        }
        return new String(chars);
    }
}
