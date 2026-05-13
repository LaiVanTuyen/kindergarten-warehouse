package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.AdminUpdateUserRequest;
import com.kindergarten.warehouse.dto.request.BlockUserRequest;
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
import com.kindergarten.warehouse.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CacheManager cacheManager;
    @Mock private MinioStorageService minioStorageService;
    @Mock private RedisOtpService redisOtpService;
    @Mock private EmailService emailService;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private Cache cache;

    @InjectMocks
    private UserService userService;

    private User adminUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L).username("admin").email("admin@test.com")
                .roles(Set.of(Role.ADMIN)).status(UserStatus.ACTIVE)
                .emailVerified(true).isDeleted(false).tokenVersion(0L)
                .build();

        normalUser = User.builder()
                .id(2L).username("user").email("user@test.com")
                .roles(Set.of(Role.USER)).status(UserStatus.ACTIVE)
                .emailVerified(true).isDeleted(false).tokenVersion(0L)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(Long userId) {
        CustomUserDetails cud = CustomUserDetails.builder().id(userId).build();
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(cud);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // =================================================================
    //  CREATE USER TESTS
    // =================================================================

    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {
        
        private UserCreationRequest request;

        @BeforeEach
        void setUpRequest() {
            request = new UserCreationRequest();
            request.setUsername("newuser");
            request.setEmail("newuser@test.com");
            request.setPassword("Password123!");
            request.setFullName("New User");
        }

        @Test
        @DisplayName("Nên throw USER_EXISTED nếu username bị trùng")
        void shouldThrow_whenUsernameExists() {
            when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_EXISTED));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Nên tạo user thành công (ACTIVE, emailVerified=true) khi hợp lệ")
        void shouldCreateUserSuccessfully() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userMapper.toResponse(any())).thenReturn(new UserResponse());

            when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            userService.createUser(request);

            verify(userRepository).save(argThat(user -> 
                user.getStatus() == UserStatus.ACTIVE &&
                user.getEmailVerified() == true &&
                user.getRoles().contains(Role.USER)
            ));
        }
    }

    // =================================================================
    //  TOGGLE BLOCK USER TESTS
    // =================================================================

    @Nested
    @DisplayName("toggleBlockUser()")
    class ToggleBlockUserTests {

        @Test
        @DisplayName("Nên throw CANNOT_MODIFY_SELF nếu admin cố gắng block chính mình")
        void shouldThrow_whenBlockSelf() {
            mockSecurityContext(adminUser.getId());
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

            assertThatThrownBy(() -> userService.toggleBlockUser(adminUser.getId(), new BlockUserRequest()))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CANNOT_MODIFY_SELF));
        }

        @Test
        @DisplayName("Nên throw LAST_ADMIN_PROTECTED nếu khóa admin active cuối cùng")
        void shouldThrow_whenBlockLastActiveAdmin() {
            mockSecurityContext(999L); // Auth is someone else
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(userRepository.countActiveUsersByRoleExcluding(Role.ADMIN, adminUser.getId())).thenReturn(0L);

            assertThatThrownBy(() -> userService.toggleBlockUser(adminUser.getId(), new BlockUserRequest()))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED));
        }

        @Test
        @DisplayName("Nên khóa user thành công, đổi status=BLOCKED, tăng tokenVersion")
        void shouldBlockUserSuccessfully() {
            mockSecurityContext(1L); // Auth is Admin(1)
            when(userRepository.findById(normalUser.getId())).thenReturn(Optional.of(normalUser));
            
            BlockUserRequest req = new BlockUserRequest();
            req.setReason("Spam");

            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(cacheManager.getCache("users")).thenReturn(cache);

            UpdateResult<UserResponse> result = userService.toggleBlockUser(normalUser.getId(), req);

            assertThat(result.getMessageKey()).isEqualTo("user.blocked");
            verify(userRepository).save(argThat(user -> 
                user.getStatus() == UserStatus.BLOCKED &&
                user.getBlockedReason().equals("Spam") &&
                user.getTokenVersion() == 1L
            ));
        }

        @Test
        @DisplayName("Nên mở khóa user đang bị block thành công")
        void shouldUnblockUserSuccessfully() {
            mockSecurityContext(1L);
            normalUser.setStatus(UserStatus.BLOCKED);
            normalUser.setBlockedReason("Spam");
            
            when(userRepository.findById(normalUser.getId())).thenReturn(Optional.of(normalUser));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(cacheManager.getCache("users")).thenReturn(cache);

            UpdateResult<UserResponse> result = userService.toggleBlockUser(normalUser.getId(), null);

            assertThat(result.getMessageKey()).isEqualTo("user.unblocked");
            verify(userRepository).save(argThat(user -> 
                user.getStatus() == UserStatus.ACTIVE &&
                user.getBlockedReason() == null &&
                user.getBlockedAt() == null
            ));
        }
    }

    // =================================================================
    //  UPDATE USER TESTS
    // =================================================================

    @Nested
    @DisplayName("updateUser()")
    class UpdateUserTests {

        private AdminUpdateUserRequest request;

        @BeforeEach
        void setUpReq() {
            request = new AdminUpdateUserRequest();
        }

        @Test
        @DisplayName("Nên ném LAST_ADMIN_PROTECTED khi tước quyền ADMIN của admin cuối cùng")
        void shouldThrow_whenRemoveAdminRoleFromLastAdmin() {
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(userRepository.countActiveUsersByRoleExcluding(Role.ADMIN, adminUser.getId())).thenReturn(0L);

            request.setRoles(Set.of("USER")); // Hạ quyền từ ADMIN xuống USER

            assertThatThrownBy(() -> userService.updateUser(adminUser.getId(), request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED));
        }

        @Test
        @DisplayName("Nên cập nhật user thành công và tăng tokenVersion khi roles thay đổi")
        void shouldUpdateUser_andIncrementToken_whenRoleChanged() {
            when(userRepository.findById(normalUser.getId())).thenReturn(Optional.of(normalUser));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(cacheManager.getCache("users")).thenReturn(cache);

            request.setFullName("Updated Name");
            request.setRoles(Set.of("ADMIN", "USER"));

            userService.updateUser(normalUser.getId(), request);

            verify(userRepository).save(argThat(user -> 
                user.getFullName().equals("Updated Name") &&
                user.getRoles().contains(Role.ADMIN) &&
                user.getTokenVersion() == 1L // Tăng version vì đổi quyền
            ));
        }
    }
}
