package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.dto.response.UserResponse;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthResponseDto login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        User user = userRepository.findByUsername(loginDto.getUsername())
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.USER_NOT_FOUND));

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .isActive(user.getIsActive())
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
        user.setRole(registerDto.getRole() != null ? registerDto.getRole() : Role.USER);
        user.setIsActive(true);

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
                .role(savedUser.getRole())
                .isActive(savedUser.getIsActive())
                .build();

        return new AuthResponseDto(userResponse, token, null);
    }
}
