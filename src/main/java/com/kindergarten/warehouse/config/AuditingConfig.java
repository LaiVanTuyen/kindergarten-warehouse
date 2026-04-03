package com.kindergarten.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }

    public static class SpringSecurityAuditorAware implements AuditorAware<Long> {

        @Override
        public Optional<Long> getCurrentAuditor() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null ||
                    !authentication.isAuthenticated() ||
                    authentication.getPrincipal().equals("anonymousUser")) {
                return Optional.empty();
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof com.kindergarten.warehouse.security.CustomUserDetails) {
                return Optional.ofNullable(((com.kindergarten.warehouse.security.CustomUserDetails) principal).getId());
            }

            return Optional.empty();
        }
    }
}
