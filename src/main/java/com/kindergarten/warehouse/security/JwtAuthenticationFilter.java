package com.kindergarten.warehouse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService,
            org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        System.out.println("DEBUG: Filter processing URI: " + request.getRequestURI());

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(token))) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } catch (Exception e) {
                // Redis might be down, ignore blacklist check for now or log error
                System.out.println("DEBUG: Redis check failed: " + e.getMessage());
            }

            String username = jwtTokenProvider.getUsername(token);
            System.out.println("DEBUG: Token valid for user: " + username);

            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                System.out.println("DEBUG: SecurityContext set for user: " + username);
            } catch (Exception e) {
                System.out.println("DEBUG: UserDetails load failed: " + e.getMessage());
                // User not found or other error, ignore and let the request proceed anonymously
                // The SecurityFilterChain will handle 401/403 if the endpoint requires auth
            }
        } else {
            System.out.println("DEBUG: Token invalid or not found. Token: " + (token != null ? "present" : "null"));
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        // 1. Try to get from Cookie first
        if (request.getCookies() != null) {
            System.out.println("DEBUG: Cookies found: " + request.getCookies().length);
            for (Cookie cookie : request.getCookies()) {
                System.out.println("DEBUG: Cookie Name: " + cookie.getName());
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        } else {
            System.out.println("DEBUG: No cookies found in request.");
        }

        // 2. Fallback to Header (Useful for Postman without cookie support or legacy)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
