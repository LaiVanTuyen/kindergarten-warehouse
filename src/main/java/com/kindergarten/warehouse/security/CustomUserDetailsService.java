package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

        private final UserRepository userRepository;
        private final MessageSource messageSource;

        public CustomUserDetailsService(UserRepository userRepository, MessageSource messageSource) {
                this.userRepository = userRepository;
                this.messageSource = messageSource;
        }

        @Override
        public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
                // Try finding by email first
                User user = userRepository.findByEmail(usernameOrEmail)
                                .orElse(null);

                // If not found by email, try finding by username
                if (user == null) {
                        user = userRepository.findByUsernameAndIsDeletedFalse(usernameOrEmail)
                                        .orElseThrow(() -> new UsernameNotFoundException(messageSource
                                                        .getMessage("error.user.not_found.username",
                                                                        new Object[] { usernameOrEmail },
                                                                        LocaleContextHolder.getLocale())));
                }

                Set<GrantedAuthority> authorities = user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.name()))
                                .collect(java.util.stream.Collectors.toSet());

                return new org.springframework.security.core.userdetails.User(
                                user.getUsername(),
                                user.getPassword(),
                                user.getStatus() == com.kindergarten.warehouse.entity.UserStatus.ACTIVE, // Enabled
                                true, // Account Non Expired
                                true, // Credentials Non Expired
                                true, // Account Non Locked
                                authorities);
        }
}
