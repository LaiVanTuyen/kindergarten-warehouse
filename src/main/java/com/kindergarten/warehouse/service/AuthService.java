package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.LoginDto;
import com.kindergarten.warehouse.dto.request.RegisterDto;
import com.kindergarten.warehouse.dto.response.AuthResponseDto;
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
            org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.messageSource = messageSource;
        this.redisTemplate = redisTemplate;
    }

    public AuthResponseDto login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        User user = userRepository.findByUsername(loginDto.getUsername()).orElseThrow();

        return new AuthResponseDto(
                token,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name());
    }

    public String register(RegisterDto registerDto) {
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            throw new RuntimeException(
                    messageSource.getMessage("auth.username.taken", null, LocaleContextHolder.getLocale()));
        }

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setFullName(registerDto.getFullName());
        user.setRole(Role.USER);
        user.setIsActive(true);

        userRepository.save(user);

        return messageSource.getMessage("auth.user.registered", null, LocaleContextHolder.getLocale());
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
