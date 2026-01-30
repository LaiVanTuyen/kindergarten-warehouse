package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.UserMapper;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;

    public AuthResponseDto login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        User user = userRepository.findByEmail(loginDto.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return new AuthResponseDto(userMapper.toResponse(user), token, null);
    }

    public AuthResponseDto register(RegisterDto registerDto) {
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByEmail(registerDto.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setEmail(registerDto.getEmail());
        user.setFullName(registerDto.getFullName());
        user.setRoles(Collections.singleton(Role.USER));
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Auto-login: Generate token
        Authentication authentication = new UsernamePasswordAuthenticationToken(savedUser.getUsername(), null, null);
        String token = jwtTokenProvider.generateToken(authentication);

        return new AuthResponseDto(userMapper.toResponse(savedUser), token, null);
    }

    public void logout(String token) {
        if (token == null)
            return;
        Date expirationDate = jwtTokenProvider.getExpirationDate(token);
        long ttl = expirationDate.getTime() - new Date().getTime();
        if (ttl > 0) {
            redisTemplate.opsForValue().set("kindergarten:blacklist:" + token, "blacklisted", ttl,
                    TimeUnit.MILLISECONDS);
        }
    }
}
