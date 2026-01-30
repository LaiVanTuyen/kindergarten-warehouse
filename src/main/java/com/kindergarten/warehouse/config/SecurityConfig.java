package com.kindergarten.warehouse.config;

import com.kindergarten.warehouse.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> {
                    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                    csrfTokenRepository.setCookiePath("/");

                    // Use standard RequestHandler to support raw Angular tokens (disabling XOR
                    // BREACH protection default)
                    org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler requestHandler = new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler();
                    requestHandler.setCsrfRequestAttributeName(null);

                    csrf.csrfTokenRepository(csrfTokenRepository)
                            .csrfTokenRequestHandler(requestHandler)
                            .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                                    "/api/v1/auth/logout", "/error");
                })
                .authorizeHttpRequests(auth -> auth
                        // Public Endpoints
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/logout", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/topics/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/age-groups/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/banners/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/resources/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/resources/*/view").permitAll()
                        // Authenticated User Endpoints (Profile)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/change-password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/avatar").authenticated()

                        // Admin Endpoints
                        .requestMatchers("/api/v1/users/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/categories/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/topics/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/topics/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/topics/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/topics/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/banners/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/banners/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/banners/**").hasAuthority("ADMIN")
                        // Teacher/Admin Endpoints (Resource Upload)
                        .requestMatchers(HttpMethod.POST, "/api/v1/resources/**").hasAnyAuthority("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/resources/**").hasAnyAuthority("ADMIN", "TEACHER")
                        // Any other request must be authenticated
                        .anyRequest().authenticated())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("accessToken", "JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_OK);
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"code\":1011,\"message\":\"Unauthorized\",\"result\":null}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"code\":1012,\"message\":\"Forbidden\",\"result\":null}");
                        }));

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Filter to ensure CSRF Token is generated and sent (Lazy by default in Spring
        // Security 6)
        http.addFilterAfter((request, response, chain) -> {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Explicitly access the token to force generation/persistence
                csrfToken.getToken();
            }
            chain.doFilter(request, response);
        }, CsrfFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        java.util.List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
