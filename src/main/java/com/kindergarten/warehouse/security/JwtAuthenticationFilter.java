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

        System.err.println(">>> DEBUG: FILTER START: " + request.getRequestURI());

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            System.err.println(">>> DEBUG: Token found: " + token.substring(0, Math.min(token.length(), 10)) + "...");
            if (jwtTokenProvider.validateToken(token)) {
                System.err.println(">>> DEBUG: Token is VALID");
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey("kindergarten:blacklist:" + token))) {
                        System.err.println(">>> DEBUG: Token is BLACKLISTED");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println(">>> DEBUG: Redis check failed: " + e.getMessage());
                }

                String username = jwtTokenProvider.getUsername(token);
                System.err.println(">>> DEBUG: Username from token: " + username);

                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    System.err.println(">>> DEBUG: UserDetails loaded: " + userDetails.getUsername() + ", Authorities: "
                            + userDetails.getAuthorities());

                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    System.err.println(">>> DEBUG: SecurityContext SET SUCCESS");
                } catch (Exception e) {
                    System.err.println(">>> DEBUG: UserDetails load failed: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println(">>> DEBUG: Token validation FAILED");
            }
        } else {
            System.err.println(">>> DEBUG: No Token found in request");
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    System.err.println(">>> DEBUG: Found accessToken cookie");
                    return cookie.getValue();
                }
            }
            System.err.println(">>> DEBUG: Cookies present but accessToken NOT found. Cookies: "
                    + java.util.Arrays.stream(request.getCookies()).map(Cookie::getName)
                            .collect(java.util.stream.Collectors.joining(", ")));
        } else {
            System.err.println(">>> DEBUG: No Cookies in request");
        }

        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            System.err.println(">>> DEBUG: Found Authorization Header");
            return bearerToken.substring(7);
        }
        return null;
    }
}
