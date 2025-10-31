package uk.gov.justice.laa.dstew.access.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasAppRole_returnsTrue_whenRoleIsPresent() {
        setAuthenticationWithAuthorities("APPROLE_ADMIN");

        EffectiveAuthorizationProvider provider = securityConfig.authProvider();
        assertTrue(provider.hasAppRole("ADMIN"));
    }

    @Test
    void hasAppRole_returnsFalse_whenRoleIsNotPresent() {
        setAuthenticationWithAuthorities("APPROLE_USER");

        EffectiveAuthorizationProvider provider = securityConfig.authProvider();
        assertFalse(provider.hasAppRole("ADMIN"));
    }

    @Test
    void hasAnyAppRole_returnsTrue_whenOneRoleMatches() {
        setAuthenticationWithAuthorities("APPROLE_USER", "APPROLE_MANAGER");

        EffectiveAuthorizationProvider provider = securityConfig.authProvider();
        assertTrue(provider.hasAnyAppRole("ADMIN", "MANAGER"));
    }

    @Test
    void hasAnyAppRole_returnsFalse_whenNoRolesMatch() {
        setAuthenticationWithAuthorities("APPROLE_GUEST");

        EffectiveAuthorizationProvider provider = securityConfig.authProvider();
        assertFalse(provider.hasAnyAppRole("ADMIN", "USER"));
    }

    @Test
    void hasAppRole_returnsFalse_whenNoAuthenticationPresent() {
        SecurityContextHolder.clearContext();

        EffectiveAuthorizationProvider provider = securityConfig.authProvider();
        assertFalse(provider.hasAppRole("ADMIN"));
    }

    private void setAuthenticationWithAuthorities(String... roles) {
        var authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        var authentication = new TestingAuthenticationToken("user", "password", authorities);
        authentication.setAuthenticated(true);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
