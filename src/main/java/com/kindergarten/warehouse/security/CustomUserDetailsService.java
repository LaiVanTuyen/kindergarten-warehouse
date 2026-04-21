package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MessageSource messageSource;

    /**
     * Load user by username hoặc email. Chỉ trả về user chưa bị xóa mềm.
     * Trạng thái user (ACTIVE/BLOCKED/PENDING/INACTIVE) được ánh xạ qua flag
     * {@code enabled} để Spring Security xử lý, nhưng business code nên check
     * status rõ ràng tại AuthService để trả ra error code cụ thể.
     */
    @Override
    @Cacheable(value = "users", key = "#usernameOrEmail")
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findActiveByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException(messageSource.getMessage(
                        "error.user.not_found.username",
                        new Object[] { usernameOrEmail },
                        LocaleContextHolder.getLocale())));

        Set<String> roles = user.getRoles() == null
                ? Set.of()
                : user.getRoles().stream().map(Role::name).collect(Collectors.toSet());

        return CustomUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .enabled(user.isLoginAllowed())
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .tokenVersion(user.getTokenVersion() == null ? 0L : user.getTokenVersion())
                .roles(roles)
                .build();
    }
}
