package uk.gov.justice.laa.dstew.access.infrastructure.security;

import java.util.Arrays;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.AccessPolicy;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;

/** Spring Security implementation of AccessPolicy. */
@Component
public class SpringSecurityAccessPolicy implements AccessPolicy {

  private static final String ROLE_PREFIX = "ROLE_";
  private static final java.util.Map<RequiredRole, String> ROLE_MAPPING =
      java.util.Map.of(
          RequiredRole.API_CASEWORKER, "LAA_CASEWORKER",
          RequiredRole.ADMIN, "ADMIN",
          RequiredRole.SUPERVISOR, "SUPERVISOR",
          RequiredRole.AUTHENTICATED, "AUTHENTICATED");

  @Override
  public void enforce(EnforceRole annotation) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new AccessDeniedException("Not authenticated");
    }

    RequiredRole[] anyOf = annotation.anyOf();
    RequiredRole[] allOf = annotation.allOf();

    if (anyOf.length > 0 && !hasAnyRole(auth, anyOf)) {
      throw new AccessDeniedException("Access denied: requires one of " + Arrays.toString(anyOf));
    }

    if (allOf.length > 0 && !hasAllRoles(auth, allOf)) {
      throw new AccessDeniedException("Access denied: requires all of " + Arrays.toString(allOf));
    }
  }

  private boolean hasAnyRole(Authentication auth, RequiredRole[] roles) {
    return Arrays.stream(roles).anyMatch(r -> hasRole(auth, r));
  }

  private boolean hasAllRoles(Authentication auth, RequiredRole[] roles) {
    return Arrays.stream(roles).allMatch(r -> hasRole(auth, r));
  }

  private boolean hasRole(Authentication auth, RequiredRole role) {
    String mappedRole = ROLE_MAPPING.getOrDefault(role, role.name());
    return auth.getAuthorities().stream()
        .anyMatch(
            a ->
                a.getAuthority().equals(mappedRole)
                    || a.getAuthority().equals(ROLE_PREFIX + mappedRole));
  }
}
