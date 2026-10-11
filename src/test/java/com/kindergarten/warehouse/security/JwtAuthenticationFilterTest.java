package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthenticationFilterTest {
    @Test
    void staleCookieCannotPreventCredentialLogin() throws Exception {
        var provider = mock(JwtTokenProvider.class);
        var users = mock(org.springframework.security.core.userdetails.UserDetailsService.class);
        var revocation = mock(TokenRevocationService.class);
        var filter = new JwtAuthenticationFilter(provider, users, revocation);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setCookies(new Cookie("accessToken", "revoked"));
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(provider, users, revocation);
    }

    @Test
    void revokedCookieStillCannotAccessProtectedEndpoint() throws Exception {
        var provider = mock(JwtTokenProvider.class);
        var users = mock(org.springframework.security.core.userdetails.UserDetailsService.class);
        var revocation = mock(TokenRevocationService.class);
        when(provider.validateToken("revoked")).thenReturn(true);
        when(provider.getUserId("revoked")).thenReturn(1L);
        when(provider.getTokenVersion("revoked")).thenReturn(0L);
        when(revocation.checkTokenState("revoked", 1L, 0L)).thenReturn(TokenRevocationService.State.REVOKED);
        var filter = new JwtAuthenticationFilter(provider, users, revocation);
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setCookies(new Cookie("accessToken", "revoked"));
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain, users);
    }
}
