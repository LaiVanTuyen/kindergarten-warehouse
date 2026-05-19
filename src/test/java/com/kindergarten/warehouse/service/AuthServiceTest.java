package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.UserMapper;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.CustomUserDetails;
import com.kindergarten.warehouse.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link AuthService}.
 *
 * <p>Dùng {@code @ExtendWith(MockitoExtension.class)} — không load Spring Context,
 * không cần DB, Redis, hay Email server. Tất cả dependency được mock bằng Mockito.
 *
 * <p>RequestUtils.getClientIpAddress() và getUserAgent() được stub qua MockedStatic
 * để tránh NPE khi không có HttpServletRequest trong test context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    // ----------------------------------------------------------------- mocks

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private UserMapper userMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private EmailService emailService;
    @Mock private RedisOtpService otpService;
    @Mock private RateLimitService rateLimitService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private CacheManager cacheManager;

    @InjectMocks
    private AuthService authService;

    // ---------------------------------------------------------------- fixtures

    private LoginDto validLoginDto;
    private User activeUser;
    private CustomUserDetails activeUserDetails;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        validLoginDto = new LoginDto();
        validLoginDto.setEmail("teacher@test.com");
        validLoginDto.setPassword("SecurePass123!");

        activeUser = User.builder()
                .id(1L)
                .username("teacher01")
                .email("teacher@test.com")
                .password("$2a$10$encodedPassword")
                .roles(Set.of(Role.USER))
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .tokenVersion(0L)
                .build();

        activeUserDetails = CustomUserDetails.builder()
                .id(1L)
                .username("teacher01")
                .email("teacher@test.com")
                .roles(Set.of("USER"))
                .tokenVersion(0L)
                .enabled(true)
                .build();

        userResponse = new UserResponse();
    }

    // =================================================================
    //  LOGIN TESTS
    // =================================================================

    @Nested
    @DisplayName("login()")
    class LoginTests {

        /**
         * Khi RateLimitService throw AppException(LOGIN_ATTEMPT_EXCEEDED),
         * AuthService phải propagate exception đó mà không gọi UserRepository.
         */
        @Test
        @DisplayName("Nên throw LOGIN_ATTEMPT_EXCEEDED khi đã vượt rate limit")
        void login_shouldThrow_whenRateLimitExceeded() {
            doThrow(AppException.withRetryAfter(ErrorCode.LOGIN_ATTEMPT_EXCEEDED, 60L))
                    .when(rateLimitService).ensureLoginAllowed(anyString(), anyString());

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.LOGIN_ATTEMPT_EXCEEDED));
            }

            // UserRepository không được gọi khi đã rate-limit
            verify(userRepository, never()).findActiveByUsernameOrEmail(anyString());
        }

        /**
         * Khi không tìm thấy user theo email/username,
         * phải ghi nhận failed login và throw UNAUTHENTICATED.
         */
        @Test
        @DisplayName("Nên throw UNAUTHENTICATED và ghi nhận failed login khi không tìm thấy user")
        void login_shouldThrow_whenUserNotFound() {
            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.empty());

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
            }

            verify(rateLimitService).recordFailedLogin(anyString(), anyString());
        }

        /**
         * Khi password không khớp, phải ghi nhận failed login và throw UNAUTHENTICATED.
         */
        @Test
        @DisplayName("Nên throw UNAUTHENTICATED và ghi nhận failed login khi sai mật khẩu")
        void login_shouldThrow_whenPasswordMismatch() {
            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
            }

            verify(rateLimitService).recordFailedLogin(anyString(), anyString());
        }

        /**
         * User bị soft-delete phải bị từ chối đăng nhập với ACCOUNT_DELETED.
         */
        @Test
        @DisplayName("Nên throw ACCOUNT_DELETED khi user đã bị xóa mềm")
        void login_shouldThrow_whenAccountDeleted() {
            User deletedUser = User.builder()
                    .id(2L).username("deleted01").email("del@test.com")
                    .password("$2a$10$encoded").status(UserStatus.ACTIVE)
                    .emailVerified(true).isDeleted(true).tokenVersion(0L)
                    .roles(Set.of(Role.USER)).build();

            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(deletedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.ACCOUNT_DELETED));
            }
        }

        /**
         * User bị BLOCKED phải bị từ chối với ACCOUNT_BLOCKED.
         */
        @Test
        @DisplayName("Nên throw ACCOUNT_BLOCKED khi user bị block")
        void login_shouldThrow_whenAccountBlocked() {
            User blockedUser = User.builder()
                    .id(3L).username("blocked01").email("blocked@test.com")
                    .password("$2a$10$encoded").status(UserStatus.BLOCKED)
                    .emailVerified(true).isDeleted(false).tokenVersion(0L)
                    .roles(Set.of(Role.USER)).build();

            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(blockedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.ACCOUNT_BLOCKED));
            }
        }

        /**
         * User ở trạng thái INACTIVE bị xử lý như BLOCKED (theo logic assertAccountLoginable).
         */
        @Test
        @DisplayName("Nên throw ACCOUNT_BLOCKED khi user INACTIVE")
        void login_shouldThrow_whenAccountInactive() {
            User inactiveUser = User.builder()
                    .id(4L).username("inactive01").email("inactive@test.com")
                    .password("$2a$10$encoded").status(UserStatus.INACTIVE)
                    .emailVerified(true).isDeleted(false).tokenVersion(0L)
                    .roles(Set.of(Role.USER)).build();

            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(inactiveUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.ACCOUNT_BLOCKED));
            }
        }

        /**
         * User PENDING (chưa xác thực email) phải bị từ chối với ACCOUNT_PENDING.
         */
        @Test
        @DisplayName("Nên throw ACCOUNT_PENDING khi user chưa hoàn tất đăng ký")
        void login_shouldThrow_whenAccountPending() {
            User pendingUser = User.builder()
                    .id(5L).username("pending01").email("pending@test.com")
                    .password("$2a$10$encoded").status(UserStatus.PENDING)
                    .emailVerified(false).isDeleted(false).tokenVersion(0L)
                    .roles(Set.of(Role.USER)).build();

            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(pendingUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.ACCOUNT_PENDING));
            }
        }

        /**
         * User ACTIVE nhưng chưa verify email phải bị từ chối với EMAIL_NOT_VERIFIED.
         */
        @Test
        @DisplayName("Nên throw EMAIL_NOT_VERIFIED khi email chưa xác thực")
        void login_shouldThrow_whenEmailNotVerified() {
            User unverifiedUser = User.builder()
                    .id(6L).username("unverified01").email("unverified@test.com")
                    .password("$2a$10$encoded").status(UserStatus.ACTIVE)
                    .emailVerified(false).isDeleted(false).tokenVersion(0L)
                    .roles(Set.of(Role.USER)).build();

            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(unverifiedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                assertThatThrownBy(() -> authService.login(validLoginDto))
                        .isInstanceOf(AppException.class)
                        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
            }
        }

        /**
         * Happy path: tất cả điều kiện đúng →
         * - trả về AuthResponseDto với accessToken không rỗng
         * - rateLimitService.reset() được gọi
         * - auditLogService.saveLog() được gọi
         */
        @Test
        @DisplayName("Nên trả về AuthResponseDto với token khi login thành công")
        void login_shouldReturnAuthResponse_whenCredentialsValid() {
            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(userDetailsService.loadUserByUsername(activeUser.getUsername()))
                    .thenReturn(activeUserDetails);
            when(jwtTokenProvider.generateToken(activeUserDetails)).thenReturn("mocked.jwt.token");
            when(userMapper.toResponse(activeUser)).thenReturn(userResponse);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                AuthResponseDto result = authService.login(validLoginDto);

                assertThat(result).isNotNull();
                assertThat(result.getAccessToken()).isEqualTo("mocked.jwt.token");
                assertThat(result.getUser()).isSameAs(userResponse);
            }

            verify(rateLimitService).reset(anyString(), anyString());
            verify(auditLogService).saveLog(eq("LOGIN"), eq("teacher01"), anyString(), anyString(), anyString(), anyString());
        }

        /**
         * Kiểm tra JwtTokenProvider được gọi đúng 1 lần với CustomUserDetails đúng.
         * Không test nội dung JWT (đó là trách nhiệm của JwtTokenProviderTest).
         */
        @Test
        @DisplayName("Nên gọi JwtTokenProvider với CustomUserDetails từ UserDetailsService")
        void login_shouldCallJwtProvider_withCorrectUserDetails() {
            when(userRepository.findActiveByUsernameOrEmail(anyString()))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(userDetailsService.loadUserByUsername(activeUser.getUsername()))
                    .thenReturn(activeUserDetails);
            when(jwtTokenProvider.generateToken(any())).thenReturn("any.token");
            when(userMapper.toResponse(any())).thenReturn(userResponse);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                authService.login(validLoginDto);
            }

            verify(jwtTokenProvider, times(1)).generateToken(activeUserDetails);
        }
    }

    // =================================================================
    //  REGISTER TESTS
    // =================================================================

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        private RegisterDto validRegisterDto;

        @BeforeEach
        void setUpRegister() {
            validRegisterDto = new RegisterDto();
            validRegisterDto.setUsername("newteacher");
            validRegisterDto.setEmail("new@test.com");
            validRegisterDto.setPassword("StrongPass1!");
            validRegisterDto.setFullName("New Teacher");
        }

        @Test
        @DisplayName("Nên throw USER_EXISTED khi username đã tồn tại")
        void register_shouldThrow_whenUsernameExists() {
            when(userRepository.existsByUsername("newteacher")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRegisterDto))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_EXISTED));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Nên throw EMAIL_EXISTED khi email đã tồn tại")
        void register_shouldThrow_whenEmailExists() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail("new@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRegisterDto))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_EXISTED));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Nên tạo user với status PENDING và emailVerified=false khi đăng ký thành công")
        void register_shouldCreateUser_withPendingStatus() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            when(otpService.generateOtp(any(), anyString())).thenReturn("123456");
            when(userMapper.toResponse(any())).thenReturn(userResponse);

            // Capture user được lưu để kiểm tra trạng thái
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                savedUser.setId(99L); // giả lập DB set ID
                return savedUser;
            });

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                AuthResponseDto result = authService.register(validRegisterDto);

                assertThat(result).isNotNull();
                // Register không trả về token (null) — chờ verify email
                assertThat(result.getAccessToken()).isNull();
            }

            // Verify user được save với status PENDING
            verify(userRepository).save(argThat(user ->
                    user.getStatus() == UserStatus.PENDING
                    && Boolean.FALSE.equals(user.getEmailVerified())
                    && user.getRoles().contains(Role.USER)
            ));
        }
    }

    // =================================================================
    //  LOGOUT TESTS
    // =================================================================

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Không nên làm gì khi token null hoặc blank")
        void logout_shouldDoNothing_whenTokenBlank() {
            authService.logout(null);
            authService.logout("   ");

            verifyNoInteractions(jwtTokenProvider, redisTemplate);
        }

        @Test
        @DisplayName("Nên blacklist token hợp lệ còn hiệu lực vào Redis")
        void logout_shouldBlacklistToken_whenTokenValid() {
            String fakeToken = "valid.jwt.token";
            long futureTime = System.currentTimeMillis() + 60_000L;
            java.util.Date futureDate = new java.util.Date(futureTime);

            when(jwtTokenProvider.getExpirationDate(fakeToken)).thenReturn(futureDate);
            when(jwtTokenProvider.getUsername(fakeToken)).thenReturn("teacher01");

            org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
                    mock(org.springframework.data.redis.core.ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            try (MockedStatic<com.kindergarten.warehouse.util.RequestUtils> utils =
                         mockStatic(com.kindergarten.warehouse.util.RequestUtils.class)) {
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getClientIpAddress).thenReturn("127.0.0.1");
                utils.when(com.kindergarten.warehouse.util.RequestUtils::getUserAgent).thenReturn("JUnit");

                authService.logout(fakeToken);
            }

            verify(valueOps).set(
                    contains(fakeToken),
                    eq("blacklisted"),
                    anyLong(),
                    eq(java.util.concurrent.TimeUnit.MILLISECONDS)
            );
        }
    }
}
