package com.kindergarten.warehouse.config;

import com.kindergarten.warehouse.security.CustomUserDetails;
import com.kindergarten.warehouse.service.RedisLastActiveService;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {

                // Lấy userId trực tiếp từ Principal (đã được load bởi JwtAuthenticationFilter)
                // => Không cần thêm DB query nào ở đây
                redisLastActiveService.updateLastActive(userDetails.getId());
            }
        } catch (Exception e) {
            // Do not block request if tracking fails
            logger.error("Failed to track last active time", e);
        }

        filterChain.doFilter(request, response);
    }
}
