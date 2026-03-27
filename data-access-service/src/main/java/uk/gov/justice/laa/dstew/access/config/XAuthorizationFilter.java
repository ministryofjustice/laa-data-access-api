package uk.gov.justice.laa.dstew.access.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that validates the {@code X-Authorization} header JWT and merges its
 * {@code LAA_APP_ROLES} claim into the existing {@link Authentication}.
 *
 * <p>This filter contains no feature-flag logic — it simply does its job when present.
 * It is only instantiated by {@link XAuthorizationConfig} when {@code feature.x-authz=true}.
 *
 * <p>Logic:
 * <ol>
 *   <li>Read the {@code X-Authorization} header; absent → 401.
 *   <li>Decode and validate the JWT via the injected {@code xAuthorizationJwtDecoder}; invalid → 401.
 *   <li>Compare the {@code sub} claim against the existing OBO principal name; mismatch → 401.
 *   <li>Extract {@code LAA_APP_ROLES}; absent → pass through (403 from method security).
 *   <li>Merge {@code APPROLE_*} authorities into the existing {@link Authentication} and update
 *       the {@link SecurityContextHolder}.
 * </ol>
 */
public class XAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(XAuthorizationFilter.class);

    private static final String X_AUTHORIZATION_HEADER = "X-Authorization";
    private static final String APP_ROLES_CLAIM = "LAA_APP_ROLES";
    private static final String AUTHORITY_PREFIX = "APPROLE_";

    private final JwtDecoder xAuthorizationJwtDecoder;

    public XAuthorizationFilter(JwtDecoder xAuthorizationJwtDecoder) {
        this.xAuthorizationJwtDecoder = xAuthorizationJwtDecoder;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String token = request.getHeader(X_AUTHORIZATION_HEADER);
        if (token == null) {
            log.warn("X-Authorization header missing");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Jwt xAuthJwt;
        try {
            xAuthJwt = xAuthorizationJwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("X-Authorization token invalid: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String xAuthSub = xAuthJwt.getSubject();
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        String oboName = (existingAuth != null) ? existingAuth.getName() : null;

        if (oboName == null || !oboName.equals(xAuthSub)) {
            log.warn("X-Authorization sub claim '{}' does not match OBO token principal '{}'",
                    xAuthSub, oboName);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        List<String> appRoles = xAuthJwt.getClaimAsStringList(APP_ROLES_CLAIM);
        if (appRoles == null || appRoles.isEmpty()) {
            // Authenticated but no roles — let the request proceed; method security will return 403
            chain.doFilter(request, response);
            return;
        }

        Set<GrantedAuthority> mergedAuthorities = new HashSet<>(existingAuth.getAuthorities());
        appRoles.forEach(role -> mergedAuthorities.add(new SimpleGrantedAuthority(AUTHORITY_PREFIX + role)));

        Authentication merged = new UsernamePasswordAuthenticationToken(
                existingAuth.getPrincipal(),
                existingAuth.getCredentials(),
                mergedAuthorities
        );
        SecurityContextHolder.getContext().setAuthentication(merged);

        chain.doFilter(request, response);
    }
}


