package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.JwtTokenProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;
    private final JwtTokenProvider jwtTokenProvider;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public AuthService(AuthenticationManager authenticationManager,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            MessageSource messageSource,
            org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate,
            org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate1) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.messageSource = messageSource;
        this.redisTemplate = redisTemplate1;
    }

    public AuthResponseDto login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        User user = userRepository.findByEmail(loginDto.getEmail())
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .roles(user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()))
                .status(user.getStatus().name())
                .isDeleted(user.getIsDeleted())
                .createdAt(user.getCreatedAt())
                .build();

        return new AuthResponseDto(userResponse, token, null);
    }

    public AuthResponseDto register(RegisterDto registerDto) {
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByEmail(registerDto.getEmail())) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.EMAIL_EXISTED);
        }

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setEmail(registerDto.getEmail());
        user.setFullName(registerDto.getFullName());
        user.setRoles(java.util.Collections.singleton(Role.USER));
        user.setStatus(com.kindergarten.warehouse.entity.UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Auto-login: Generate token
        Authentication authentication = new UsernamePasswordAuthenticationToken(savedUser.getUsername(), null, null);
        String token = jwtTokenProvider.generateToken(authentication);

        UserResponse userResponse = UserResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .avatarUrl(savedUser.getAvatarUrl())
                .roles(savedUser.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()))
                .status(savedUser.getStatus().name())
                .isDeleted(savedUser.getIsDeleted())
                .build();

        return new AuthResponseDto(userResponse, token, null);
    }

    public void logout(String token) {
        if (token == null)
            return;
        Date expirationDate = jwtTokenProvider.getExpirationDate(token);
        long ttl = expirationDate.getTime() - new Date().getTime();
        if (ttl > 0) {
            redisTemplate.opsForValue().set(token, "blacklisted", ttl, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
}
