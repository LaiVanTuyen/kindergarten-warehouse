package com.kindergarten.warehouse.config;

import com.kindergarten.warehouse.service.RedisLastActiveService;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LastActiveFilter extends OncePerRequestFilter {

    private final RedisLastActiveService redisLastActiveService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && authentication.isAuthenticated()) {

                String username = authentication.getName();
                // To optimize, we might want to cache userId in the UserDetails or Token to
                // avoid DB lookup here.
                // For now, let's look it up or rely on a cache.
                // Assuming username is unique.
                // Optimization: In a real heavy system, extract userId from JWT directly if
                // possible.
                // But here, let's use UserRepository (it should be cached by 2nd level cache or
                // we can trust the Service to handle it)

                // For this implementation, let's just lookup ID quickly.
                // Or better, let's assume we can get ID from Principal if we cast it.
                // (Depending on UserDetails implementation)

                userRepository.findByUsername(username).ifPresent(user -> {
                    redisLastActiveService.updateLastActive(user.getId());
                });
            }
        } catch (Exception e) {
            // Do not block request if tracking fails
            logger.error("Failed to track last active time", e);
        }

        filterChain.doFilter(request, response);
    }
}
